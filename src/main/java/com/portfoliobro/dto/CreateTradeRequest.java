package com.portfoliobro.dto;

import com.portfoliobro.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

public record CreateTradeRequest(
        @NotNull TradeSide side,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9.-]{0,14}") String ticker,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 8) BigDecimal shares,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal pricePerShare,
        @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal fees,
        @NotNull Instant executedAt) {
    public CreateTradeRequest {
        if (ticker != null) ticker = ticker.strip().toUpperCase(Locale.ROOT);
        // PostgreSQL timestamps retain microseconds: reject finer timestamps rather than lose retry identity.
        if (shares != null) shares = shares.stripTrailingZeros();
        if (pricePerShare != null) pricePerShare = pricePerShare.stripTrailingZeros();
        fees = fees == null ? BigDecimal.ZERO : fees.stripTrailingZeros();
    }
    public TradeEntry toEntry() { return new TradeEntry(side, ticker, shares, pricePerShare, fees, executedAt); }
}
