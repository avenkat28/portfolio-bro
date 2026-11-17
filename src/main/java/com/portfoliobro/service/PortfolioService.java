package com.portfoliobro.service;

import com.portfoliobro.domain.Portfolio;
import com.portfoliobro.dto.*;
import com.portfoliobro.exception.PortfolioNotFoundException;
import com.portfoliobro.repository.PortfolioRepository;
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

    public PortfolioService(PortfolioRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
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
    public void delete(UUID id) { repository.delete(find(id)); }

    private Portfolio find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new PortfolioNotFoundException(id));
    }
}
