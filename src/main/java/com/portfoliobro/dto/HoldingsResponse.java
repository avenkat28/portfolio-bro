package com.portfoliobro.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HoldingsResponse(UUID portfolioId, String currency, Instant calculatedAt,
                               boolean valuationComplete, List<HoldingResponse> holdings) {}
