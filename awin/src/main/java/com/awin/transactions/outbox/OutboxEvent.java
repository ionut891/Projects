package com.awin.transactions.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_event",
        indexes = {@Index(name = "ix_outbox_unpublished", columnList = "published_at, created_at")})
public class OutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "payload", nullable = false, columnDefinition = "CLOB")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected OutboxEvent() {
        // for JPA
    }

    public OutboxEvent(UUID aggregateId, String type, String payload, Instant createdAt) {
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.createdAt = createdAt;
        this.attempts = 0;
    }

    public void markPublished(Instant when) {
        this.publishedAt = when;
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attempts += 1;
        this.lastError = truncate(error);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
