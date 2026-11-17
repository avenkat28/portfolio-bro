package com.portfoliobro.repository;

import com.portfoliobro.domain.Trade;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, UUID> {
    Optional<Trade> findByPortfolioIdAndIdempotencyKey(UUID portfolioId, UUID key);
    List<Trade> findByPortfolioIdOrderBySequenceNumberAsc(UUID portfolioId);
    Page<Trade> findByPortfolioId(UUID portfolioId, Pageable pageable);
    Page<Trade> findByPortfolioIdAndTicker(UUID portfolioId, String ticker, Pageable pageable);
    boolean existsByPortfolioId(UUID portfolioId);
}
