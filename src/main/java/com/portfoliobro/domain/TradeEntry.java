package com.portfoliobro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Immutable calculation input, independent of HTTP and persistence. */
public record TradeEntry(TradeSide side, String ticker, BigDecimal shares,
                         BigDecimal pricePerShare, BigDecimal fees, Instant executedAt) {
    public TradeEntry {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(executedAt, "executedAt");
        ticker = ticker.strip().toUpperCase(Locale.ROOT);
        if (!ticker.matches("[A-Z][A-Z0-9.-]{0,14}")) {
            throw new IllegalArgumentException("Invalid ticker");
        }
        validateDecimal(shares, "shares", 8, 11, false);
        validateDecimal(pricePerShare, "pricePerShare", 6, 13, false);
        validateDecimal(fees, "fees", 6, 13, true);
    }

    private static void validateDecimal(BigDecimal value, String field, int scale, int integerDigits,
                                        boolean zeroAllowed) {
        Objects.requireNonNull(value, field);
        BigDecimal normalized = value.stripTrailingZeros();
        if (value.signum() < 0 || (!zeroAllowed && value.signum() == 0)
                || normalized.scale() > scale
                || value.abs().compareTo(BigDecimal.TEN.pow(integerDigits)) >= 0) {
            throw new IllegalArgumentException("Invalid " + field + " precision or value");
        }
    }
}
