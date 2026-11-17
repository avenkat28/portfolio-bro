package com.portfoliobro.service;

import com.portfoliobro.domain.*;
import com.portfoliobro.dto.*;
import com.portfoliobro.exception.*;
import com.portfoliobro.repository.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TradeService {
    private final PortfolioRepository portfolios;
    private final TradeRepository trades;
    private final Clock clock;
    private final EntityManager entityManager;
    private final AverageCostCalculator calculator = new AverageCostCalculator();

    public record Submission(TradeResponse trade, boolean replayed) {}

    public TradeService(PortfolioRepository portfolios, TradeRepository trades, Clock clock, EntityManager entityManager) {
        this.portfolios = portfolios;
        this.trades = trades;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional
    public Submission record(UUID portfolioId, UUID key, CreateTradeRequest request) {
        // SET LOCAL applies to this transaction only. Lock the portfolio even for the first ticker trade.
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();
        portfolios.findForUpdate(portfolioId).orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        TradeEntry entry;
        try { entry = request.toEntry(); }
        catch (IllegalArgumentException exception) { throw new InvalidTradeException(exception.getMessage()); }
        if (entry.executedAt().getNano() % 1000 != 0) {
            throw new InvalidTradeException("executedAt supports at most microsecond precision");
        }
        Optional<Trade> existing = trades.findByPortfolioIdAndIdempotencyKey(portfolioId, key);
        if (existing.isPresent()) {
            if (!existing.get().matches(entry)) {
                throw new TradeConflictException("IDEMPOTENCY_CONFLICT", "Idempotency key already used with another trade");
            }
            return new Submission(TradeResponse.from(existing.get()), true);
        }
        if (entry.executedAt().isAfter(clock.instant())) {
            throw new InvalidTradeException("executedAt cannot be in the future");
        }
        List<Trade> history = trades.findByPortfolioIdOrderBySequenceNumberAsc(portfolioId);
        long sequence = 1;
        if (!history.isEmpty()) {
            Trade last = history.getLast();
            if (entry.executedAt().isBefore(last.toEntry().executedAt())) {
                throw new TradeConflictException("TRADE_CHRONOLOGY", "Trade predates the latest portfolio trade");
            }
            sequence = Math.addExact(last.getSequenceNumber(), 1);
        }
        List<TradeEntry> entries = new ArrayList<>(history.stream().map(Trade::toEntry).toList());
        entries.add(entry);
        try { calculator.calculate(entries); }
        catch (OversellException exception) { throw exception; }
        catch (IllegalArgumentException exception) { throw new InvalidTradeException(exception.getMessage()); }
        Trade saved = trades.saveAndFlush(new Trade(portfolioId, sequence, key, entry, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
        return new Submission(TradeResponse.from(saved), false);
    }

    public PageResponse<TradeResponse> list(UUID portfolioId, String ticker, int page, int size) {
        if (!portfolios.existsById(portfolioId)) throw new PortfolioNotFoundException(portfolioId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("sequenceNumber"));
        Page<Trade> result = ticker == null ? trades.findByPortfolioId(portfolioId, pageable)
                : trades.findByPortfolioIdAndTicker(portfolioId, ticker, pageable);
        return PageResponse.from(result.map(TradeResponse::from));
    }
}
