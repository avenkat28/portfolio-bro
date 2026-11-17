package com.portfoliobro.dto;

import java.time.Instant;
import java.util.UUID;

public record PerformanceResponse(UUID portfolioId, String currency, Instant calculatedAt,
                                  boolean valuationComplete, String remainingCostBasis,
                                  String realizedProfitLoss, String totalPortfolioValue,
                                  String unrealizedProfitLoss, String totalProfitLoss,
                                  String knownQuotedValue) {}
