package com.portfoliobro.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(BigDecimal price, String currency, Instant observedAt, String source,
                          Availability availability) {
    public enum Availability { AVAILABLE, MISSING, UNAVAILABLE }
    public static MarketQuote missing(String source) {
        return new MarketQuote(null, "USD", null, source, Availability.MISSING);
    }
    public static MarketQuote unavailable(String source) {
        return new MarketQuote(null, "USD", null, source, Availability.UNAVAILABLE);
    }
}
