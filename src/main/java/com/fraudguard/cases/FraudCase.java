package com.fraudguard.cases;

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
@Table(name = "fraud_case")
public class FraudCase {

    public enum Status { ABERTO, APROVADO, BLOQUEADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_id", nullable = false, updatable = false)
    private Long decisionId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String analyst;
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected FraudCase() {}

    public static FraudCase open(Long decisionId) {
        FraudCase c = new FraudCase();
        c.decisionId = decisionId;
        c.status = Status.ABERTO;
        return c;
    }

    public void resolve(Status finalStatus, String analyst, String note) {
        this.status = finalStatus;
        this.analyst = analyst;
        this.note = note;
        this.resolvedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getDecisionId() { return decisionId; }
    public Status getStatus() { return status; }
    public String getAnalyst() { return analyst; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
