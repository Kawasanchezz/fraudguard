package com.fraudguard.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FailSafePolicyTest {

    private final FailSafePolicy policy = new FailSafePolicy(new BigDecimal("5000"), 40);

    private static EvaluationResult base(Outcome o, int score) {
        return new EvaluationResult("tx-1", o, score, List.of(new RuleHit("X", score, "x")), 3);
    }

    @ParameterizedTest
    @CsvSource({
            "4999.99,APROVAR,APROVAR",   // valor baixo segue aprovado
            "5000,APROVAR,REVISAR",      // limite inclusivo escalona
            "90000,APROVAR,REVISAR",
            "90000,REVISAR,REVISAR",     // nunca rebaixa
            "90000,BLOQUEAR,BLOQUEAR",
            "10,BLOQUEAR,BLOQUEAR"
    })
    void outcomeMatrix(String amount, Outcome before, Outcome after) {
        var r = policy.apply(base(before, before == Outcome.BLOQUEAR ? 90 : 0), new BigDecimal(amount));
        assertThat(r.outcome()).isEqualTo(after);
    }

    @Test
    void alwaysRecordsTheFailSafeReasonAndKeepsVersion() {
        var r = policy.apply(base(Outcome.APROVAR, 0), new BigDecimal("10"));
        assertThat(r.reasons()).extracting(RuleHit::code).contains(FailSafePolicy.CODE);
        assertThat(r.rulesetVersion()).isEqualTo(3);
    }

    @Test
    void escalatedScoreReachesReviewThreshold() {
        var r = policy.apply(base(Outcome.APROVAR, 10), new BigDecimal("6000"));
        assertThat(r.score()).isEqualTo(40);
    }
}
