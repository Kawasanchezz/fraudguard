package com.fraudguard.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "fraud_decision")
public class FraudDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private String transactionId;

    private int score;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_fired", nullable = false)
    private List<RuleHit> rulesFired;

    @Column(name = "ruleset_version", nullable = false)
    private int rulesetVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected FraudDecision() {}

    public FraudDecision(EvaluationResult r) {
        this.transactionId = r.transactionId();
        this.score = r.score();
        this.outcome = r.outcome();
        this.rulesFired = r.reasons();
        this.rulesetVersion = r.rulesetVersion();
    }

    public Long getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public int getScore() { return score; }
    public Outcome getOutcome() { return outcome; }
    public List<RuleHit> getRulesFired() { return rulesFired; }
    public int getRulesetVersion() { return rulesetVersion; }
}
