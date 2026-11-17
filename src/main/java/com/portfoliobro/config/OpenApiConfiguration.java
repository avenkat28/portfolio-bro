package com.portfoliobro.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI portfolioApi() {
        return new OpenAPI().info(new Info().title("PortfolioBro API").version("v1")
                .description("Local single-user paper portfolio tracker. Currency is USD and timestamps use UTC. "
                        + "POST returns 201 with Location; DELETE returns 204. Missing portfolios return 404. "
                        + "Invalid requests return 400. Errors use application/problem+json with a code "
                        + "and optional fieldErrors. PATCH leaves omitted/null fields unchanged; an empty "
                        + "description clears it. Pagination is zero-based, defaults to 20, and permits "
                        + "1–100 records, ordered by creation time then ID. Authentication and trades "
                        + "are not included in this milestone."));
    }
}
