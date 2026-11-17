package com.portfoliobro.service;

import com.portfoliobro.domain.*;
import com.portfoliobro.exception.PortfolioNotFoundException;
import com.portfoliobro.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioSnapshotService {
    private final PortfolioRepository portfolios;
    private final TradeRepository trades;
    private final AverageCostCalculator calculator = new AverageCostCalculator();
    public PortfolioSnapshotService(PortfolioRepository portfolios, TradeRepository trades) {
        this.portfolios = portfolios;
        this.trades = trades;
    }

    /** Materialize the ordered history before any provider call, without holding write locks. */
    @Transactional(readOnly = true)
    public Map<String, PositionSnapshot> load(UUID portfolioId) {
        if (!portfolios.existsById(portfolioId)) throw new PortfolioNotFoundException(portfolioId);
        return calculator.calculate(trades.findByPortfolioIdOrderBySequenceNumberAsc(portfolioId)
                .stream().map(Trade::toEntry).toList());
    }
}
