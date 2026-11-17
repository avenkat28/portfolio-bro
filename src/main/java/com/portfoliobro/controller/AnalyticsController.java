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

    @GetMapping("/holdings")
    public HoldingsResponse holdings(@PathVariable UUID portfolioId) { return analytics.holdings(portfolioId); }

    @GetMapping("/performance")
    public PerformanceResponse performance(@PathVariable UUID portfolioId) { return analytics.performance(portfolioId); }
}
