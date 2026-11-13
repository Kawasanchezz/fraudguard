package com.fraudguard.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "decision_outcome")
public class DecisionOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutcomeType type;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    protected DecisionOutcome() {}

    public DecisionOutcome(String transactionId, OutcomeType type, Instant occurredAt) {
        this.transactionId = transactionId;
        this.type = type;
        this.occurredAt = occurredAt;
    }

    public void update(OutcomeType type, Instant occurredAt) {
        this.type = type;
        this.occurredAt = occurredAt;
        this.recordedAt = Instant.now();
    }

    public String getTransactionId() { return transactionId; }
    public OutcomeType getType() { return type; }
    public Instant getOccurredAt() { return occurredAt; }
}
