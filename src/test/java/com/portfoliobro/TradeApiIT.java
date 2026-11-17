package com.portfoliobro;

import com.portfoliobro.dto.CreatePortfolioRequest;
import com.portfoliobro.service.PortfolioService;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TradeApiIT {
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    static { DATABASE.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired PortfolioService portfolios;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    final JsonMapper json = JsonMapper.builder().build();
    UUID portfolio;
    static final String TIME = "2026-01-01T12:00:00Z";

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM trade"); jdbc.update("DELETE FROM portfolio");
        portfolio = portfolios.create(new CreatePortfolioRequest("Trades", null)).id();
    }
    String path() { return "/api/v1/portfolios/" + portfolio + "/trades"; }
    String body(String side, String shares, String time) {
        return "{\"side\":\"" + side + "\",\"ticker\":\" aapl \",\"shares\":\"" + shares
                + "\",\"pricePerShare\":\"100\",\"fees\":\"1\",\"executedAt\":\"" + time + "\"}";
    }
    ResultActions submit(UUID key, String body) throws Exception {
        return mvc.perform(post(path()).header("Idempotency-Key", key).contentType("application/json").content(body));
    }
    long count() { return jdbc.queryForObject("SELECT count(*) FROM trade", Long.class); }

    @Test void recordsTradesReplaysAndPreservesHistory() throws Exception {
        UUID key = UUID.randomUUID();
        String buy = body("BUY", "10", TIME);
        String first = submit(key, buy).andExpect(status().isCreated()).andExpect(jsonPath("$.sequenceNumber").value(1))
                .andExpect(jsonPath("$.ticker").value("AAPL")).andExpect(jsonPath("$.shares").value("10"))
                .andReturn().getResponse().getContentAsString();
        submit(UUID.randomUUID(), body("SELL", "2", "2026-01-02T12:00:00Z")).andExpect(status().isCreated());
        String replay = submit(key, buy.replace("\"10\"", "\"10.000000000\""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(replay)).isEqualTo(json.readTree(first));
        submit(key, body("BUY", "11", TIME)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(get(path()).param("ticker", "aapl").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].sequenceNumber").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(delete("/api/v1/portfolios/" + portfolio)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_HAS_TRADES"));
        assertThat(count()).isEqualTo(2);
    }

    @Test void invalidTradesNeverWriteAndFailedKeysCanBeRetried() throws Exception {
        UUID key = UUID.randomUUID();
        submit(key, body("SELL", "1", TIME)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OVERSELL"));
        assertThat(count()).isZero();
        submit(key, body("BUY", "1", TIME)).andExpect(status().isCreated());
        submit(UUID.randomUUID(), body("SELL", "1.00000001", TIME)).andExpect(status().isConflict());
        submit(UUID.randomUUID(), body("BUY", "1", "2025-01-01T00:00:00Z")).andExpect(status().isConflict());
        submit(UUID.randomUUID(), body("BUY", "1", Instant.now().plusSeconds(3600).toString())).andExpect(status().isBadRequest());
        submit(UUID.randomUUID(), body("BUY", "1", "2026-01-01T12:00:00.123456789Z")).andExpect(status().isBadRequest());
        for (String shares : new String[]{"0", "-1", "0.000000001", "100000000000"}) {
            submit(UUID.randomUUID(), body("BUY", shares, TIME)).andExpect(status().isBadRequest());
        }
        submit(UUID.randomUUID(), body("BUY", "1", TIME).replace("\"100\"", "\"0.0000001\""))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path()).contentType("application/json").content(body("BUY", "1", TIME)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path()).header("Idempotency-Key", "bad").contentType("application/json").content(body("BUY", "1", TIME)))
                .andExpect(status().isBadRequest());
        assertThat(count()).isEqualTo(1);
    }

    @Test void defaultFeesAndPortfolioScopedKeys() throws Exception {
        UUID key = UUID.randomUUID();
        String request = body("BUY", "1", TIME).replace(",\"fees\":\"1\"", "");
        submit(key, request).andExpect(status().isCreated()).andExpect(jsonPath("$.fees").value("0"));
        portfolio = portfolios.create(new CreatePortfolioRequest("Other", null)).id();
        submit(key, request).andExpect(status().isCreated());
        mvc.perform(get(path()).param("ticker", "MSFT")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get(path()).param("ticker", "bad ticker")).andExpect(status().isBadRequest());
        mvc.perform(get(path()).param("size", "101")).andExpect(status().isBadRequest());
        portfolio = UUID.randomUUID();
        submit(UUID.randomUUID(), request).andExpect(status().isNotFound());
        mvc.perform(get(path())).andExpect(status().isNotFound());
    }

    @Test void concurrentSellsCannotOversell() throws Exception {
        submit(UUID.randomUUID(), body("BUY", "10", TIME)).andExpect(status().isCreated());
        List<Integer> statuses = concurrently(UUID.randomUUID(), UUID.randomUUID(), body("SELL", "7", TIME));
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sum(CASE side WHEN 'BUY' THEN shares ELSE -shares END) FROM trade", java.math.BigDecimal.class))
                .isEqualByComparingTo("3");
    }

    @Test void concurrentDuplicateRequestsCreateExactlyOneRow() throws Exception {
        UUID key = UUID.randomUUID();
        assertThat(concurrently(key, key, body("BUY", "10", TIME))).containsExactlyInAnyOrder(201, 200);
        assertThat(count()).isEqualTo(1);
    }

    List<Integer> concurrently(UUID firstKey, UUID secondKey, String request) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (UUID key : List.of(firstKey, secondKey)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                    return submit(key, request).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
        }
    }

    @Test void deletionRacingFirstTradeCannotLoseHistory() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> trade = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                return submit(UUID.randomUUID(), body("BUY", "1", TIME)).andReturn().getResponse().getStatus();
            });
            Future<Integer> deletion = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                return mvc.perform(delete("/api/v1/portfolios/" + portfolio)).andReturn().getResponse().getStatus();
            });
            start.countDown();
            int recorded = trade.get(10, TimeUnit.SECONDS);
            int deleted = deletion.get(10, TimeUnit.SECONDS);
            if (recorded == 201) {
                assertThat(deleted).isEqualTo(409);
                assertThat(count()).isEqualTo(1);
            } else {
                assertThat(recorded).isEqualTo(404);
                assertThat(deleted).isEqualTo(204);
                assertThat(count()).isZero();
            }
        }
    }

    @Test void recordedTradesHaveNoMutationEndpoints() throws Exception {
        String first = submit(UUID.randomUUID(), body("BUY", "1", TIME)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String tradePath = path() + "/" + json.readTree(first).get("id").asText();
        // No trade-resource mutation routes exist (a missing route returns 404).
        mvc.perform(delete(tradePath)).andExpect(status().isNotFound());
        mvc.perform(patch(tradePath).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        assertThat(count()).isEqualTo(1);
    }

    @Test void heldPortfolioLockReturnsRetryable503() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT id FROM portfolio WHERE id = ? FOR UPDATE")) {
                statement.setObject(1, portfolio);
                statement.executeQuery().close();
            }
            submit(UUID.randomUUID(), body("BUY", "1", TIME)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("LOCK_TIMEOUT"));
            connection.rollback();
        }
        assertThat(count()).isZero();
    }

    @Test void holdingsAndPerformanceReplayRealTradesWithMockPrices() throws Exception {
        submit(UUID.randomUUID(), body("BUY", "10", TIME).replace("\"fees\":\"1\"", "\"fees\":\"2\""))
                .andExpect(status().isCreated());
        submit(UUID.randomUUID(), body("BUY", "5", TIME).replace("\"100\"", "\"120\""))
                .andExpect(status().isCreated());
        submit(UUID.randomUUID(), body("SELL", "6", TIME).replace("\"100\"", "\"130\"")
                .replace("\"fees\":\"1\"", "\"fees\":\"2\""))
                .andExpect(status().isCreated());
        String base = "/api/v1/portfolios/" + portfolio;
        mvc.perform(get(base + "/holdings")).andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[0].shares").value("9"))
                .andExpect(jsonPath("$.holdings[0].averageCost").value("106.866667"))
                .andExpect(jsonPath("$.holdings[0].quote.source").value("MOCK"));
        mvc.perform(get(base + "/performance")).andExpect(status().isOk())
                .andExpect(jsonPath("$.valuationComplete").value(true))
                .andExpect(jsonPath("$.totalPortfolioValue").value("1125.00"))
                .andExpect(jsonPath("$.realizedProfitLoss").value("136.80"))
                .andExpect(jsonPath("$.unrealizedProfitLoss").value("163.20"))
                .andExpect(jsonPath("$.totalProfitLoss").value("300.00"));
        submit(UUID.randomUUID(), body("BUY", "1", TIME).replace("aapl", "unknown"))
                .andExpect(status().isCreated());
        mvc.perform(get(base + "/performance")).andExpect(status().isOk())
                .andExpect(jsonPath("$.valuationComplete").value(false))
                .andExpect(jsonPath("$.totalPortfolioValue").doesNotExist())
                .andExpect(jsonPath("$.knownQuotedValue").value("1125.00"));
    }

    @Test void emptyAndMissingPortfolioAnalytics() throws Exception {
        mvc.perform(get("/api/v1/portfolios/" + portfolio + "/performance")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPortfolioValue").value("0.00"));
        mvc.perform(get("/api/v1/portfolios/" + UUID.randomUUID() + "/holdings"))
                .andExpect(status().isNotFound());
    }

    @Test void databaseEnforcesForeignKeysUniquenessAndChecks() throws Exception {
        UUID key = UUID.randomUUID();
        submit(key, body("BUY", "1", TIME)).andExpect(status().isCreated());
        assertThatThrownBy(() -> jdbc.update("DELETE FROM portfolio WHERE id = ?", portfolio))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO trade SELECT gen_random_uuid(), portfolio_id, sequence_number+1, side, ticker, shares, price_per_share, fees, executed_at, created_at, idempotency_key FROM trade"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE trade SET shares = -1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
