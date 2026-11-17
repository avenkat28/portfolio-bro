package com.portfoliobro.marketdata;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

/** Synthetic fixtures, never represented as live market prices. No network or API key required. */
@Component
public class MockMarketPriceProvider implements MarketPriceProvider {
    private static final Map<String, BigDecimal> PRICES = Map.of(
            "AAPL", new BigDecimal("125"), "MSFT", new BigDecimal("450"),
            "BRK.B", new BigDecimal("500"));
    private final Clock clock;
    public MockMarketPriceProvider(Clock clock) { this.clock = clock; }

    @Override
    public Map<String, MarketQuote> getQuotes(Set<String> tickers) {
        Map<String, MarketQuote> quotes = new HashMap<>();
        var now = clock.instant();
        for (String ticker : tickers) {
            BigDecimal price = PRICES.get(ticker);
            quotes.put(ticker, price == null ? MarketQuote.missing("MOCK")
                    : new MarketQuote(price, "USD", now, "MOCK", MarketQuote.Availability.AVAILABLE));
        }
        return Map.copyOf(quotes);
    }
}
