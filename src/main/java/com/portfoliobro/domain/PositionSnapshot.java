package com.portfoliobro.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Unrounded accounting output. Closed positions retain their realized P&L. */
public record PositionSnapshot(String ticker, BigDecimal shares, BigDecimal remainingCostBasis,
                               BigDecimal realizedProfitLoss) {
    public BigDecimal averageCost() {
        return shares.signum() == 0 ? BigDecimal.ZERO
                : remainingCostBasis.divide(shares, 18, RoundingMode.HALF_EVEN);
    }
}
