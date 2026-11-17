package com.portfoliobro;

import com.portfoliobro.controller.PortfolioController;
import com.portfoliobro.dto.*;
import com.portfoliobro.exception.*;
import com.portfoliobro.service.PortfolioService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {
    @Mock PortfolioService service;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new PortfolioController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void createsWithLocationAndNormalizedName() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-17T12:00:00Z");
        when(service.create(any())).thenReturn(new PortfolioResponse(id, "Savings", null, "USD", now, now));
        mvc.perform(post("/api/v1/portfolios").contentType("application/json").content("{\"name\":\" Savings \"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/portfolios/" + id))
                .andExpect(jsonPath("$.currency").value("USD"));
        verify(service).create(new CreatePortfolioRequest("Savings", null));
    }

    @Test void validationDoesNotInvokeService() throws Exception {
        mvc.perform(post("/api/v1/portfolios").contentType("application/json").content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        for (String body : new String[]{"{}", "{\"name\":\" \"}"}) {
            mvc.perform(patch("/api/v1/portfolios/" + UUID.randomUUID()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void malformedInputUsesSafeProblemResponse() throws Exception {
        mvc.perform(get("/api/v1/portfolios/invalid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HTTP_400"))
                .andExpect(jsonPath("$.detail").value("Malformed request or invalid parameter"));
        mvc.perform(post("/api/v1/portfolios").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test void missingPortfolioReturnsProblem() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new PortfolioNotFoundException(id));
        mvc.perform(get("/api/v1/portfolios/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test void paginationIsValidated() throws Exception {
        mvc.perform(get("/api/v1/portfolios").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/portfolios").param("page", "-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
