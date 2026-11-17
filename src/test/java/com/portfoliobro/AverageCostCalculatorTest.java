package com.portfoliobro;

import com.portfoliobro.domain.*;
import com.portfoliobro.exception.OversellException;
import com.portfoliobro.service.AverageCostCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class AverageCostCalculatorTest {
    private final AverageCostCalculator calculator = new AverageCostCalculator();
    private static final Instant TIME = Instant.parse("2026-11-17T12:00:00Z");

    private TradeEntry trade(TradeSide side, String ticker, String shares, String price, String fees) {
        return new TradeEntry(side, ticker, new BigDecimal(shares), new BigDecimal(price),
                new BigDecimal(fees), TIME);
    }

    private List<TradeEntry> example() {
        return List.of(trade(TradeSide.BUY, "AAPL", "10", "100", "2"),
                trade(TradeSide.BUY, "AAPL", "5", "120", "1"),
                trade(TradeSide.SELL, "AAPL", "6", "130", "2"));
    }

    @Test void weightedAverageIncludesBuyFees() {
        var result = calculator.calculate(example().subList(0, 2)).get("AAPL");
        assertThat(result.shares()).isEqualByComparingTo("15");
        assertThat(result.remainingCostBasis()).isEqualByComparingTo("1603");
        assertThat(result.averageCost().setScale(6, RoundingMode.HALF_EVEN)).isEqualByComparingTo("106.866667");
        assertThat(result.realizedProfitLoss()).isZero();
    }

    @Test void partialSellRemovesProportionalBasisAndSellFees() {
        var result = calculator.calculate(example()).get("AAPL");
        assertThat(result.shares()).isEqualByComparingTo("9");
        assertThat(result.remainingCostBasis()).isEqualByComparingTo("961.80");
        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("136.80");
    }

    @Test void completeSaleRetainsRealizedProfitAndClearsBasisExactly() {
        var trades = new ArrayList<>(example());
        trades.add(trade(TradeSide.SELL, "AAPL", "9", "125", "0"));
        var result = calculator.calculate(trades).get("AAPL");
        assertThat(result.shares()).isZero();
        assertThat(result.remainingCostBasis()).isZero();
        assertThat(result.averageCost()).isZero();
        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("300");
    }

    @Test void sellAtLossAndReopenPreservesRealizedHistory() {
        var result = calculator.calculate(List.of(
                trade(TradeSide.BUY, "AAPL", "2", "100", "1"),
                trade(TradeSide.SELL, "AAPL", "2", "90", "2"),
                trade(TradeSide.BUY, "AAPL", "1", "80", "0"))).get("AAPL");
        assertThat(result.shares()).isEqualByComparingTo("1");
        assertThat(result.remainingCostBasis()).isEqualByComparingTo("80");
        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("-23");
    }

    @Test void fractionalSharesNeverRoundToWholeSharesOrCentsInternally() {
        var result = calculator.calculate(List.of(
                trade(TradeSide.BUY, "AAPL", "0.00000003", "0.000001", "0"),
                trade(TradeSide.SELL, "AAPL", "0.00000001", "0.000002", "0"))).get("AAPL");
        assertThat(result.shares()).isEqualByComparingTo("0.00000002");
        assertThat(result.remainingCostBasis()).isEqualByComparingTo("0.00000000000002");
        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("0.00000000000001");
    }

    @Test void repeatedSalesAbsorbDivisionResidualOnFinalLiquidation() {
        var trades = new ArrayList<TradeEntry>();
        trades.add(trade(TradeSide.BUY, "AAPL", "3", "1", "1"));
        for (int i = 0; i < 3; i++) trades.add(trade(TradeSide.SELL, "AAPL", "1", "2", "0"));
        var result = calculator.calculate(trades).get("AAPL");
        assertThat(result.remainingCostBasis()).isZero();
        assertThat(result.shares()).isZero();
        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("2");
        assertThat(calculator.calculate(trades)).isEqualTo(calculator.calculate(trades));
    }

    @Test void allocationUsesHalfEvenAtScale18() {
        var down = calculator.calculate(List.of(
                trade(TradeSide.BUY, "AAPL", "32", "0.000001", "0.000001"),
                trade(TradeSide.SELL, "AAPL", "0.00000001", "0.000001", "0"))).get("AAPL");
        // Exact allocation 0.0000000000000103125 rounds down to even at scale 18.
        assertThat(down.realizedProfitLoss()).isEqualByComparingTo("-0.000000000000000312");
        var up = calculator.calculate(List.of(
                trade(TradeSide.BUY, "AAPL", "32", "0.000001", "0.000003"),
                trade(TradeSide.SELL, "AAPL", "0.00000001", "0.000001", "0"))).get("AAPL");
        // Exact allocation 0.0000000000000109375 rounds up to even at scale 18.
        assertThat(up.realizedProfitLoss()).isEqualByComparingTo("-0.000000000000000938");
        var repeating = calculator.calculate(List.of(
                trade(TradeSide.BUY, "AAPL", "3", "1", "1"),
                trade(TradeSide.SELL, "AAPL", "1", "1", "0"))).get("AAPL");
        assertThat(repeating.remainingCostBasis()).isEqualByComparingTo("2.666666666666666667");
    }

    @Test void tickersAreNormalizedAndPositionsAreIndependent() {
        var result = calculator.calculate(List.of(
                trade(TradeSide.BUY, " aapl ", "2", "10", "0"),
                trade(TradeSide.BUY, "BRK.B", "1", "100", "0"),
                trade(TradeSide.SELL, "AAPL", "1", "15", "0")));
        assertThat(result.keySet()).containsExactly("AAPL", "BRK.B");
        assertThat(result.get("AAPL").realizedProfitLoss()).isEqualByComparingTo("5");
        assertThat(result.get("BRK.B").remainingCostBasis()).isEqualByComparingTo("100");
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void rejectsOversellingAtAnyPointWithoutRetainingState() {
        assertThatThrownBy(() -> calculator.calculate(List.of(trade(TradeSide.SELL, "AAPL", "1", "10", "0"))))
                .isInstanceOf(OversellException.class);
        assertThatThrownBy(() -> calculator.calculate(List.of(
                trade(TradeSide.BUY, "AAPL", "1", "10", "0"),
                trade(TradeSide.SELL, "AAPL", "1.00000001", "10", "0"),
                trade(TradeSide.BUY, "AAPL", "1", "10", "0"))))
                .isInstanceOf(OversellException.class);
        assertThat(calculator.calculate(List.of())).isEmpty();
    }

    @Test void rejectsBackdatingButAllowsEqualTimesInInputOrder() {
        var later = new TradeEntry(TradeSide.BUY, "AAPL", BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ZERO, TIME.plusSeconds(1));
        assertThatThrownBy(() -> calculator.calculate(List.of(later, trade(TradeSide.BUY, "MSFT", "1", "10", "0"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chronological");
        assertThat(calculator.calculate(example())).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"0,1,0", "-1,1,0", "1,0,0", "1,-1,0", "1,1,-1",
            "0.000000001,1,0", "1,0.0000001,0", "1,1,0.0000001",
            "100000000000,1,0", "1,10000000000000,0", "1,1,10000000000000"})
    void rejectsInvalidValuesAndPrecision(String shares, String price, String fees) {
        assertThatThrownBy(() -> trade(TradeSide.BUY, "AAPL", shares, price, fees))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acceptsEquivalentTrailingZerosAndRejectsInvalidTicker() {
        assertThat(trade(TradeSide.BUY, "aapl", "1.000000000", "1.0000000", "0.0000000").ticker()).isEqualTo("AAPL");
        assertThatThrownBy(() -> trade(TradeSide.BUY, "BAD TICKER", "1", "1", "0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TradeEntry(null, "AAPL", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, TIME))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void feesCanExceedSellProceeds() {
        var result = calculator.calculate(List.of(trade(TradeSide.BUY, "AAPL", "1", "1", "0"),
                trade(TradeSide.SELL, "AAPL", "1", "1", "2"))).get("AAPL");
        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("-2");
    }
}
