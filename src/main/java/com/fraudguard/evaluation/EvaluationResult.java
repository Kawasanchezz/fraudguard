package com.fraudguard.evaluation;

import java.util.List;

public record EvaluationResult(
        String transactionId,
        Outcome outcome,
        int score,
        List<RuleHit> reasons,
        int rulesetVersion) {

    static EvaluationResult from(FraudDecision d) {
        return new EvaluationResult(d.getTransactionId(), d.getOutcome(), d.getScore(),
                d.getRulesFired(), d.getRulesetVersion());
    }
}
