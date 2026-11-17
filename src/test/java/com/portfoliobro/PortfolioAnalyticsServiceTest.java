package com.portfoliobro;

import com.portfoliobro.domain.*;
import com.portfoliobro.dto.QuoteResponse;
import com.portfoliobro.marketdata.*;
import com.portfoliobro.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalyticsServiceTest {
    static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");
    final UUID id = UUID.randomUUID();
    @Mock PortfolioSnapshotService snapshots;
    @Mock MarketPriceProvider provider;
    PortfolioAnalyticsService service;

    @BeforeEach void setup() {
        service = new PortfolioAnalyticsService(snapshots, provider, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));
    }
    PositionSnapshot position(String ticker, String shares, String basis, String realized) {
        return new PositionSnapshot(ticker, new BigDecimal(shares), new BigDecimal(basis), new BigDecimal(realized));
    }
    MarketQuote quote(String price, Instant time) {
        return new MarketQuote(new BigDecimal(price), "USD", time, "TEST", MarketQuote.Availability.AVAILABLE);
    }
    void onePosition() {
        when(snapshots.load(id)).thenReturn(Map.of("AAPL", position("AAPL", "9", "961.80", "136.80")));
    }

    @Test void freshPricesProduceExpectedPerformanceAndHoldings() {
        onePosition();
        when(provider.getQuotes(Set.of("AAPL"))).thenReturn(Map.of("AAPL", quote("125", NOW)));
        var result = service.performance(id);
        assertThat(result.valuationComplete()).isTrue();
        assertThat(result.totalPortfolioValue()).isEqualTo("1125.00");
        assertThat(result.unrealizedProfitLoss()).isEqualTo("163.20");
        assertThat(result.realizedProfitLoss()).isEqualTo("136.80");
        assertThat(result.totalProfitLoss()).isEqualTo("300.00");
        var holding = service.holdings(id).holdings().getFirst();
        assertThat(holding.averageCost()).isEqualTo("106.866667");
        assertThat(holding.quote().status()).isEqualTo(QuoteResponse.Status.FRESH);
        assertThat(holding.shares()).isEqualTo("9");
    }

    @Test void staleQuoteProvidesEstimateButNotCurrentPortfolioTotals() {
        onePosition();
        when(provider.getQuotes(Set.of("AAPL"))).thenReturn(Map.of("AAPL", quote("125", NOW.minusSeconds(901))));
        var result = service.performance(id);
        assertThat(result.valuationComplete()).isFalse();
        assertThat(result.totalPortfolioValue()).isNull();
        assertThat(result.unrealizedProfitLoss()).isNull();
        assertThat(result.totalProfitLoss()).isNull();
        assertThat(result.knownQuotedValue()).isEqualTo("1125.00");
        assertThat(result.realizedProfitLoss()).isEqualTo("136.80");
        var holding = service.holdings(id).holdings().getFirst();
        assertThat(holding.quote().status()).isEqualTo(QuoteResponse.Status.STALE);
        assertThat(holding.marketValue()).isEqualTo("1125.00");
    }

    @Test void quoteExactlyAtAgeLimitIsFresh() {
        onePosition();
        when(provider.getQuotes(Set.of("AAPL"))).thenReturn(Map.of("AAPL", quote("125", NOW.minusSeconds(900))));
        assertThat(service.performance(id).valuationComplete()).isTrue();
    }

    @Test void missingPriceDoesNotBecomeZeroOrHideAvailablePositions() {
        when(snapshots.load(id)).thenReturn(Map.of("AAPL", position("AAPL", "9", "961.80", "136.80"),
                "UNKNOWN", position("UNKNOWN", "1", "50", "0")));
        when(provider.getQuotes(Set.of("AAPL", "UNKNOWN"))).thenReturn(Map.of("AAPL", quote("125", NOW),
                "UNKNOWN", MarketQuote.missing("TEST")));
        var result = service.performance(id);
        assertThat(result.totalPortfolioValue()).isNull();
        assertThat(result.knownQuotedValue()).isEqualTo("1125.00");
        assertThat(result.remainingCostBasis()).isEqualTo("1011.80");
        var missing = service.holdings(id).holdings().get(1);
        assertThat(missing.marketValue()).isNull();
        assertThat(missing.unrealizedProfitLoss()).isNull();
        assertThat(missing.quote().status()).isEqualTo(QuoteResponse.Status.MISSING);
    }

    @Test void providerFailureStillReturnsAccounting() {
        onePosition();
        when(provider.getQuotes(Set.of("AAPL"))).thenThrow(new IllegalStateException("Simulated outage"));
        var result = service.performance(id);
        assertThat(result.totalPortfolioValue()).isNull();
        assertThat(result.remainingCostBasis()).isEqualTo("961.80");
        assertThat(result.realizedProfitLoss()).isEqualTo("136.80");
        assertThat(result.knownQuotedValue()).isEqualTo("0.00");
        assertThat(service.holdings(id).holdings().getFirst().quote().status()).isEqualTo(QuoteResponse.Status.UNAVAILABLE);
    }

    @Test void rejectsInvalidProviderValuesAsUnavailable() {
        onePosition();
        for (MarketQuote invalid : List.of(quote("0", NOW), quote("-1", NOW), quote("125", NOW.plusSeconds(1)),
                quote("125", null), new MarketQuote(BigDecimal.ONE, "EUR", NOW, "TEST", MarketQuote.Availability.AVAILABLE),
                new MarketQuote(null, "USD", NOW, "TEST", MarketQuote.Availability.AVAILABLE), MarketQuote.unavailable("TEST"))) {
            when(provider.getQuotes(Set.of("AAPL"))).thenReturn(Map.of("AAPL", invalid));
            assertThat(service.holdings(id).holdings().getFirst().quote().status()).isEqualTo(QuoteResponse.Status.UNAVAILABLE);
        }
    }

    @Test void omittedProviderResultIsUnavailable() {
        onePosition();
        when(provider.getQuotes(Set.of("AAPL"))).thenReturn(Map.of());
        assertThat(service.performance(id).valuationComplete()).isFalse();
    }

    @Test void emptyPortfolioHasCompleteZeroValuationWithoutProviderCall() {
        when(snapshots.load(id)).thenReturn(Map.of());
        var result = service.performance(id);
        assertThat(result.valuationComplete()).isTrue();
        assertThat(result.totalPortfolioValue()).isEqualTo("0.00");
        assertThat(result.totalProfitLoss()).isEqualTo("0.00");
        assertThat(service.holdings(id).holdings()).isEmpty();
        verifyNoInteractions(provider);
    }

    @Test void closedPositionsRetainRealizedProfitWithoutQuote() {
        when(snapshots.load(id)).thenReturn(Map.of("AAPL", position("AAPL", "0", "0", "300")));
        assertThat(service.holdings(id).holdings()).isEmpty();
        var result = service.performance(id);
        assertThat(result.totalPortfolioValue()).isEqualTo("0.00");
        assertThat(result.totalProfitLoss()).isEqualTo("300.00");
        assertThat(result.valuationComplete()).isTrue();
        verifyNoInteractions(provider);
    }

    @Test void aggregatesBeforeRoundingToCents() {
        when(snapshots.load(id)).thenReturn(Map.of("AAPL", position("AAPL", "0.004", "0.004", "0"),
                "MSFT", position("MSFT", "0.004", "0.004", "0")));
        when(provider.getQuotes(Set.of("AAPL", "MSFT"))).thenReturn(Map.of("AAPL", quote("1", NOW), "MSFT", quote("1", NOW)));
        var result = service.performance(id);
        assertThat(result.totalPortfolioValue()).isEqualTo("0.01");
        assertThat(result.remainingCostBasis()).isEqualTo("0.01");
    }

    @Test void mockProviderLabelsSyntheticPricesAndUnknownTickers() {
        var mock = new MockMarketPriceProvider(Clock.fixed(NOW, ZoneOffset.UTC));
        var quotes = mock.getQuotes(Set.of("AAPL", "UNKNOWN"));
        assertThat(quotes.get("AAPL").price()).isEqualByComparingTo("125");
        assertThat(quotes.get("AAPL").observedAt()).isEqualTo(NOW);
        assertThat(quotes.get("AAPL").source()).isEqualTo("MOCK");
        assertThat(quotes.get("UNKNOWN").availability()).isEqualTo(MarketQuote.Availability.MISSING);
    }
}
