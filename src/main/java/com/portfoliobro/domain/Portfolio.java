package com.portfoliobro.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portfolio")
public class Portfolio {
    @Id
    private UUID id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 1000)
    private String description;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Portfolio() {}

    public Portfolio(String name, String description, Instant now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.currency = "USD";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String description, Instant now) {
        if (name != null) this.name = name;
        if (description != null) this.description = description.isEmpty() ? null : description;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
