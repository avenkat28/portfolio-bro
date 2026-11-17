package com.portfoliobro.exception;

public class TradeConflictException extends RuntimeException {
    private final String code;
    public TradeConflictException(String code, String detail) { super(detail); this.code = code; }
    public String getCode() { return code; }
}
