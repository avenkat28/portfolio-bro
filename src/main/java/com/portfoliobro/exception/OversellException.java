package com.portfoliobro.exception;

import java.math.BigDecimal;

public class OversellException extends IllegalArgumentException {
    public OversellException(String ticker, BigDecimal requested, BigDecimal available) {
        super("Cannot sell " + requested.toPlainString() + " shares of " + ticker
                + "; available: " + available.toPlainString());
    }
}
