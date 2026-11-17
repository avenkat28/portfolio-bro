package com.portfoliobro.dto;

import java.time.Instant;

public record QuoteResponse(String price, String currency, Instant observedAt, String source, Status status) {
    public enum Status { FRESH, STALE, MISSING, UNAVAILABLE }
}
