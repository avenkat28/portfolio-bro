package com.portfoliobro.service;

import com.portfoliobro.domain.Portfolio;
import com.portfoliobro.dto.*;
import com.portfoliobro.exception.PortfolioNotFoundException;
import com.portfoliobro.repository.PortfolioRepository;
import com.portfoliobro.repository.TradeRepository;
import com.portfoliobro.exception.TradeConflictException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PortfolioService {
    private final PortfolioRepository repository;
    private final Clock clock;
    private final TradeRepository trades;
    private final EntityManager entityManager;

    public PortfolioService(PortfolioRepository repository, Clock clock, TradeRepository trades, EntityManager entityManager) {
        this.repository = repository;
        this.clock = clock;
        this.trades = trades;
        this.entityManager = entityManager;
    }

    @Transactional
    public PortfolioResponse create(CreatePortfolioRequest request) {
        return PortfolioResponse.from(repository.save(new Portfolio(request.name(), request.description(), clock.instant())));
    }

    public PageResponse<PortfolioResponse> list(int page, int size) {
        return PageResponse.from(repository.findAll(PageRequest.of(page, size,
                Sort.by("createdAt").ascending().and(Sort.by("id")))).map(PortfolioResponse::from));
    }

    public PortfolioResponse get(UUID id) { return PortfolioResponse.from(find(id)); }

    @Transactional
    public PortfolioResponse update(UUID id, UpdatePortfolioRequest request) {
        Portfolio portfolio = find(id);
        portfolio.update(request.name(), request.description(), clock.instant());
        return PortfolioResponse.from(portfolio);
    }

    @Transactional
    public void delete(UUID id) {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();
        Portfolio portfolio = repository.findForUpdate(id).orElseThrow(() -> new PortfolioNotFoundException(id));
        if (trades.existsByPortfolioId(id)) {
            throw new TradeConflictException("PORTFOLIO_HAS_TRADES", "Cannot delete a portfolio with trade history");
        }
        repository.delete(portfolio);
    }

    private Portfolio find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new PortfolioNotFoundException(id));
    }
}
