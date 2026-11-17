package com.portfoliobro.marketdata;

import java.util.Map;
import java.util.Set;

/** Batch boundary for a future cached, rate-limited external provider. */
public interface MarketPriceProvider {
    Map<String, MarketQuote> getQuotes(Set<String> tickers);
}
