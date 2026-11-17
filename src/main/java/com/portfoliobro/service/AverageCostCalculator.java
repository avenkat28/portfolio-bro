package com.portfoliobro.service;

import com.portfoliobro.domain.*;
import com.portfoliobro.exception.OversellException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/** Stateless replay in accepted order. No database, market prices, or system clock required. */
public final class AverageCostCalculator {
    private static final BigDecimal MONEY_LIMIT = BigDecimal.TEN.pow(30);

    public Map<String, PositionSnapshot> calculate(List<TradeEntry> trades) {
        Objects.requireNonNull(trades, "trades");
        Map<String, PositionSnapshot> positions = new TreeMap<>();
        Instant previousTime = null;
        for (TradeEntry trade : trades) {
            Objects.requireNonNull(trade, "trade");
            if (previousTime != null && trade.executedAt().isBefore(previousTime)) {
                throw new IllegalArgumentException("Trades must be in chronological order");
            }
            previousTime = trade.executedAt();
            PositionSnapshot previous = positions.getOrDefault(trade.ticker(),
                    new PositionSnapshot(trade.ticker(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            positions.put(trade.ticker(), apply(previous, trade));
        }
        return Collections.unmodifiableMap(positions);
    }

    private PositionSnapshot apply(PositionSnapshot position, TradeEntry trade) {
        BigDecimal quantity;
        BigDecimal basis;
        BigDecimal realized = position.realizedProfitLoss();
        BigDecimal gross = trade.shares().multiply(trade.pricePerShare());
        requireSupportedAmount(gross);
        if (trade.side() == TradeSide.BUY) {
            quantity = position.shares().add(trade.shares());
            basis = position.remainingCostBasis().add(gross).add(trade.fees());
        } else {
            int comparison = trade.shares().compareTo(position.shares());
            if (comparison > 0) {
                throw new OversellException(trade.ticker(), trade.shares(), position.shares());
            }
            // Allocate the entire balance on liquidation, absorbing prior division residuals.
            BigDecimal allocated = comparison == 0 ? position.remainingCostBasis()
                    : position.remainingCostBasis().multiply(trade.shares())
                            .divide(position.shares(), 18, RoundingMode.HALF_EVEN);
            quantity = position.shares().subtract(trade.shares());
            basis = position.remainingCostBasis().subtract(allocated);
            realized = realized.add(gross.subtract(trade.fees()).subtract(allocated));
        }
        requireSupportedAmount(basis);
        requireSupportedAmount(realized);
        return new PositionSnapshot(trade.ticker(), quantity, basis, realized);
    }

    private static void requireSupportedAmount(BigDecimal value) {
        if (value.abs().compareTo(MONEY_LIMIT) >= 0) {
            throw new IllegalArgumentException("Calculated monetary amount must be below 10^30 in magnitude");
        }
    }
}
