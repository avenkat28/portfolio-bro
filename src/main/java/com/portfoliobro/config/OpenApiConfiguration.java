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
                .description("Local single-user paper portfolio tracker. Currency is USD and timestamps use UTC. All financial response values are decimal strings. "
                        + "Creating a portfolio returns 201 with Location; new trades return 201. DELETE returns 204. Missing portfolios return 404. "
                        + "Invalid requests return 400. Errors use application/problem+json with a code "
                        + "and optional fieldErrors. PATCH leaves omitted/null fields unchanged; an empty "
                        + "description clears it. Pagination is zero-based, defaults to 20, and permits "
                        + "1–100 records. Portfolios sort by creation time then ID; trade history sorts by sequence. Trade submissions require a UUID Idempotency-Key; matching retries return 200 and conflicts return 409. "
                        + "Trades are chronological and immutable. Quotes are synthetic MOCK fixtures, not live prices. "
                        + "Incomplete valuations return null current totals. Authentication and live data are deferred."));
    }
}
