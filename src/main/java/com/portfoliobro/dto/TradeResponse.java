package com.portfoliobro.dto;

import com.portfoliobro.domain.*;
import java.time.Instant;
import java.util.UUID;

public record TradeResponse(UUID id, UUID portfolioId, long sequenceNumber, TradeSide side, String ticker,
                            String shares, String pricePerShare, String fees, Instant executedAt, Instant createdAt) {
    public static TradeResponse from(Trade trade) {
        TradeEntry entry = trade.toEntry();
        return new TradeResponse(trade.getId(), trade.getPortfolioId(), trade.getSequenceNumber(), entry.side(),
                entry.ticker(), entry.shares().stripTrailingZeros().toPlainString(), entry.pricePerShare().stripTrailingZeros().toPlainString(),
                entry.fees().stripTrailingZeros().toPlainString(), entry.executedAt(), trade.getCreatedAt());
    }
}
