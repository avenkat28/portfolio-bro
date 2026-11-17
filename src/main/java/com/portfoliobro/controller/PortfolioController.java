package com.portfoliobro.controller;

import com.portfoliobro.dto.*;
import com.portfoliobro.service.PortfolioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {
    private final PortfolioService service;

    public PortfolioController(PortfolioService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(@Valid @RequestBody CreatePortfolioRequest request) {
        PortfolioResponse result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/portfolios/" + result.id())).body(result);
    }

    @GetMapping
    public PageResponse<PortfolioResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    public PortfolioResponse get(@PathVariable UUID id) { return service.get(id); }

    @PatchMapping("/{id}")
    public PortfolioResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePortfolioRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
