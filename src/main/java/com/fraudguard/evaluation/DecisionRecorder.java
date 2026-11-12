package com.fraudguard.evaluation;

import com.fraudguard.cases.FraudCase;
import com.fraudguard.cases.FraudCaseRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Decisão e caso de revisão são gravados juntos, na mesma transação. */
@Component
public class DecisionRecorder {

    private final FraudDecisionRepository decisions;
    private final FraudCaseRepository cases;

    public DecisionRecorder(FraudDecisionRepository decisions, FraudCaseRepository cases) {
        this.decisions = decisions;
        this.cases = cases;
    }

    @Transactional
    public void record(EvaluationResult result) {
        FraudDecision decision = decisions.saveAndFlush(new FraudDecision(result));
        if (result.outcome() == Outcome.REVISAR) {
            cases.save(FraudCase.open(decision.getId()));
        }
    }
}
