package com.portfoliobro;

import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PortfolioApiIT {
    // Deliberately fail if Docker is unavailable: database checks must never silently disappear.
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    static { DATABASE.start(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach void cleanDatabase() { jdbc.update("DELETE FROM portfolio"); }

    @Test void fullLifecyclePersistsAcrossIndependentRequests() throws Exception {
        String body = mvc.perform(post("/api/v1/portfolios").contentType("application/json")
                .content("{\"name\":\"  Retirement  \",\"description\":\"Long term\"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Retirement"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(body);
        String path = "/api/v1/portfolios/" + result.get("id").asText();
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Long term"));
        mvc.perform(patch(path).contentType("application/json").content("{\"description\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Retirement"))
                .andExpect(jsonPath("$.description").doesNotExist());
        mvc.perform(patch(path).contentType("application/json").content("{\"name\":\"  Updated  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated"));
        mvc.perform(get("/api/v1/portfolios").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Updated"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio", Long.class)).isEqualTo(1);
        mvc.perform(delete(path)).andExpect(status().isNoContent());
        mvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_FOUND"));
        mvc.perform(delete(path)).andExpect(status().isNotFound());
    }

    @Test void rejectsInvalidRequestsWithoutWriting() throws Exception {
        for (String body : new String[]{"{}", "{\"name\":\"   \"}",
                json.writeValueAsString(Map.of("name", "x".repeat(101))),
                json.writeValueAsString(Map.of("name", "Valid", "description", "x".repeat(1001)))}) {
            mvc.perform(post("/api/v1/portfolios").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());
        }
        mvc.perform(get("/api/v1/portfolios/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/portfolios").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        for (String size : new String[]{"0", "101", "invalid"}) {
            mvc.perform(get("/api/v1/portfolios").param("size", size)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/portfolios").param("page", "-1")).andExpect(status().isBadRequest());
        for (String body : new String[]{"{}", "{\"name\":\" \"}", "{\"description\":null}"}) {
            mvc.perform(patch("/api/v1/portfolios/00000000-0000-0000-0000-000000000001")
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio", Long.class)).isZero();
    }

    @Test void paginationHasStableOrderAndEmptyPages() throws Exception {
        for (String name : new String[]{"One", "Two", "Three"}) {
            mvc.perform(post("/api/v1/portfolios").contentType("application/json")
                    .content(json.writeValueAsString(Map.of("name", name)))).andExpect(status().isCreated());
        }
        mvc.perform(get("/api/v1/portfolios").param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Three"))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/portfolios").param("page", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test void migrationsAndDatabaseConstraintsAreActive() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Long.class)).isEqualTo(1);
        for (String name : new String[]{"", "  ", " padded "}) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO portfolio VALUES (gen_random_uuid(), ?, NULL, 'USD', now(), now())", name))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO portfolio VALUES (gen_random_uuid(), 'Valid', NULL, 'EUR', now(), now())"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void docsAndHealthAreAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/portfolios']").exists());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
