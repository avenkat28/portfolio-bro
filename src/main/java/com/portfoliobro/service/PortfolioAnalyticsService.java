package com.portfoliobro.service;

import com.portfoliobro.domain.PositionSnapshot;
import com.portfoliobro.dto.*;
import com.portfoliobro.marketdata.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PortfolioAnalyticsService {
    private static final Logger LOG = LoggerFactory.getLogger(PortfolioAnalyticsService.class);
    private final PortfolioSnapshotService snapshots;
    private final MarketPriceProvider prices;
    private final Clock clock;
    private final Duration maxAge;

    public PortfolioAnalyticsService(PortfolioSnapshotService snapshots, MarketPriceProvider prices, Clock clock,
            @Value("${market-data.max-quote-age:PT15M}") Duration maxAge) {
        if (maxAge.isNegative() || maxAge.isZero()) throw new IllegalArgumentException("Quote age must be positive");
        this.snapshots = snapshots;
        this.prices = prices;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    public HoldingsResponse holdings(UUID portfolioId) { return calculate(portfolioId).holdings(); }
    public PerformanceResponse performance(UUID portfolioId) { return calculate(portfolioId).performance(); }

    private record Analytics(HoldingsResponse holdings, PerformanceResponse performance) {}

    private Analytics calculate(UUID portfolioId) {
        Map<String, PositionSnapshot> positions = snapshots.load(portfolioId);
        Set<String> tickers = new TreeSet<>();
        positions.values().stream().filter(p -> p.shares().signum() > 0).forEach(p -> tickers.add(p.ticker()));
        Map<String, MarketQuote> quotes = Map.of();
        if (!tickers.isEmpty()) {
            try {
                Map<String, MarketQuote> result = prices.getQuotes(Collections.unmodifiableSet(tickers));
                quotes = result == null ? Map.of() : result;
            } catch (RuntimeException exception) {
                // No exception text: future providers may embed credentials or URLs in messages.
                LOG.warn("Market price provider failed; returning incomplete valuation");
            }
        }
        Instant now = clock.instant();
        BigDecimal basis = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal quotedValue = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        boolean complete = true;
        List<HoldingResponse> holdings = new ArrayList<>();
        for (PositionSnapshot position : new TreeMap<>(positions).values()) {
            realized = realized.add(position.realizedProfitLoss());
            if (position.shares().signum() == 0) continue;
            basis = basis.add(position.remainingCostBasis());
            QuoteResponse quote = quote(quotes.get(position.ticker()), now);
            boolean quoted = quote.status() == QuoteResponse.Status.FRESH || quote.status() == QuoteResponse.Status.STALE;
            complete &= quote.status() == QuoteResponse.Status.FRESH;
            BigDecimal value = quoted ? position.shares().multiply(new BigDecimal(quote.price())) : null;
            BigDecimal gain = value == null ? null : value.subtract(position.remainingCostBasis());
            if (value != null) {
                quotedValue = quotedValue.add(value);
                unrealized = unrealized.add(gain);
            }
            holdings.add(new HoldingResponse(position.ticker(), position.shares().stripTrailingZeros().toPlainString(),
                    money(position.remainingCostBasis()), position.averageCost().setScale(6, RoundingMode.HALF_EVEN).toPlainString(),
                    money(position.realizedProfitLoss()), quote, value == null ? null : money(value),
                    gain == null ? null : money(gain)));
        }
        HoldingsResponse holdingsResponse = new HoldingsResponse(portfolioId, "USD", now, complete, List.copyOf(holdings));
        PerformanceResponse performance = new PerformanceResponse(portfolioId, "USD", now, complete, money(basis),
                money(realized), complete ? money(quotedValue) : null, complete ? money(unrealized) : null,
                complete ? money(realized.add(unrealized)) : null, money(quotedValue));
        return new Analytics(holdingsResponse, performance);
    }

    private QuoteResponse quote(MarketQuote quote, Instant now) {
        if (quote == null) return new QuoteResponse(null, "USD", null, "UNKNOWN", QuoteResponse.Status.UNAVAILABLE);
        if (quote.availability() == MarketQuote.Availability.MISSING) {
            return new QuoteResponse(null, "USD", quote.observedAt(), quote.source(), QuoteResponse.Status.MISSING);
        }
        if (quote.availability() != MarketQuote.Availability.AVAILABLE || quote.price() == null
                || quote.price().signum() <= 0 || !"USD".equals(quote.currency()) || quote.observedAt() == null
                || quote.observedAt().isAfter(now)) {
            return new QuoteResponse(null, "USD", quote.observedAt(), quote.source(), QuoteResponse.Status.UNAVAILABLE);
        }
        boolean stale = Duration.between(quote.observedAt(), now).compareTo(maxAge) > 0;
        return new QuoteResponse(quote.price().stripTrailingZeros().toPlainString(), "USD", quote.observedAt(), quote.source(),
                stale ? QuoteResponse.Status.STALE : QuoteResponse.Status.FRESH);
    }

    private static String money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_EVEN).toPlainString(); }
}
