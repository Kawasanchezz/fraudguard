package com.fraudguard.evaluation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudDecisionRepository extends JpaRepository<FraudDecision, Long> {
    Optional<FraudDecision> findByTransactionId(String transactionId);
}
