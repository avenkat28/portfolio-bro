package com.portfoliobro.dto;

import jakarta.validation.constraints.*;

public record UpdatePortfolioRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 1000) String description) {
    public UpdatePortfolioRequest {
        if (name != null) name = name.strip();
    }

    @AssertTrue(message = "Provide name or description; use an empty description to clear it")
    public boolean isUpdateProvided() { return name != null || description != null; }
}
