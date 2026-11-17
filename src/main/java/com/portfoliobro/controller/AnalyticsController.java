package com.portfoliobro.controller;

import com.portfoliobro.dto.*;
import com.portfoliobro.service.PortfolioAnalyticsService;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}")
public class AnalyticsController {
    private final PortfolioAnalyticsService analytics;
    public AnalyticsController(PortfolioAnalyticsService analytics) { this.analytics = analytics; }

    @io.swagger.v3.oas.annotations.Operation(summary = "Open holdings with synthetic price fixtures",
            description = "Stale values are estimates. Missing/unavailable quotes yield null valuation fields. "
                    + "MOCK prices are not live market prices.")
    @GetMapping("/holdings")
    public HoldingsResponse holdings(@PathVariable UUID portfolioId) { return analytics.holdings(portfolioId); }

    @io.swagger.v3.oas.annotations.Operation(summary = "Average-cost portfolio P&L",
            description = "Includes realized P&L from closed positions. When any quote is not fresh, current "
                    + "totals are null and valuationComplete is false. knownQuotedValue may include stale estimates.")
    @GetMapping("/performance")
    public PerformanceResponse performance(@PathVariable UUID portfolioId) { return analytics.performance(portfolioId); }
}
