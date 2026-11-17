package com.portfoliobro.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade")
public class Trade {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID portfolioId;
    @Column(nullable = false, updatable = false) private long sequenceNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4, updatable = false) private TradeSide side;
    @Column(nullable = false, length = 15, updatable = false) private String ticker;
    @Column(nullable = false, precision = 19, scale = 8, updatable = false) private BigDecimal shares;
    @Column(nullable = false, precision = 19, scale = 6, updatable = false) private BigDecimal pricePerShare;
    @Column(nullable = false, precision = 19, scale = 6, updatable = false) private BigDecimal fees;
    @Column(nullable = false, updatable = false) private Instant executedAt;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false, updatable = false) private UUID idempotencyKey;

    protected Trade() {}

    public Trade(UUID portfolioId, long sequenceNumber, UUID key, TradeEntry entry, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.portfolioId = portfolioId;
        this.sequenceNumber = sequenceNumber;
        this.idempotencyKey = key;
        this.side = entry.side();
        this.ticker = entry.ticker();
        this.shares = entry.shares();
        this.pricePerShare = entry.pricePerShare();
        this.fees = entry.fees();
        this.executedAt = entry.executedAt();
        this.createdAt = createdAt;
    }

    public TradeEntry toEntry() { return new TradeEntry(side, ticker, shares, pricePerShare, fees, executedAt); }
    public boolean matches(TradeEntry entry) {
        return side == entry.side() && ticker.equals(entry.ticker())
                && shares.compareTo(entry.shares()) == 0 && pricePerShare.compareTo(entry.pricePerShare()) == 0
                && fees.compareTo(entry.fees()) == 0 && executedAt.equals(entry.executedAt());
    }
    public UUID getId() { return id; }
    public UUID getPortfolioId() { return portfolioId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public Instant getCreatedAt() { return createdAt; }
}
