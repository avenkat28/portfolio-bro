package com.portfoliobro.controller;

import com.portfoliobro.dto.*;
import com.portfoliobro.service.TradeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/trades")
public class TradeController {
    private final TradeService service;
    public TradeController(TradeService service) { this.service = service; }

    @io.swagger.v3.oas.annotations.Operation(summary = "Record a chronological paper trade",
            description = "Requires a UUID Idempotency-Key. Returns 201 for a new trade, 200 for an identical retry, "
                    + "409 for overselling, chronology, or idempotency conflicts, and 503 for a lock timeout. "
                    + "Retry with the same key. executedAt supports up to six fractional second digits.")
    @PostMapping
    public ResponseEntity<TradeResponse> record(@PathVariable UUID portfolioId,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody CreateTradeRequest request) {
        TradeService.Submission result = service.record(portfolioId, key, request);
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(result.trade());
    }

    @GetMapping
    public PageResponse<TradeResponse> list(@PathVariable UUID portfolioId,
            @RequestParam(required = false) String ticker,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (ticker != null) {
            ticker = ticker.strip().toUpperCase(Locale.ROOT);
            if (!ticker.matches("[A-Z][A-Z0-9.-]{0,14}")) {
                throw new com.portfoliobro.exception.InvalidTradeException("Invalid ticker filter");
            }
        }
        return service.list(portfolioId, ticker, page, size);
    }
}
