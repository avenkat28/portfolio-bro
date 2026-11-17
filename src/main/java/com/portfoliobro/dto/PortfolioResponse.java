package com.portfoliobro.dto;

import com.portfoliobro.domain.Portfolio;
import java.time.Instant;
import java.util.UUID;

public record PortfolioResponse(UUID id, String name, String description, String currency,
                                Instant createdAt, Instant updatedAt) {
    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(portfolio.getId(), portfolio.getName(), portfolio.getDescription(),
                portfolio.getCurrency(), portfolio.getCreatedAt(), portfolio.getUpdatedAt());
    }
}
