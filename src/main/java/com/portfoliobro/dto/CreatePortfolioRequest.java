package com.portfoliobro.dto;

import jakarta.validation.constraints.*;

public record CreatePortfolioRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description) {
    public CreatePortfolioRequest {
        if (name != null) name = name.strip();
    }
}
