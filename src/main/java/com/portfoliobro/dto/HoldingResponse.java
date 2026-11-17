package com.portfoliobro.dto;

public record HoldingResponse(String ticker, String shares, String remainingCostBasis, String averageCost,
                              String realizedProfitLoss, QuoteResponse quote, String marketValue,
                              String unrealizedProfitLoss) {}
