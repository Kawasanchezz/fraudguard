package com.fraudguard.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudguard.evaluation.Outcome;
import org.junit.jupiter.api.Test;

class ClassificationTest {

    @Test
    void fraudCaughtIsTruePositive() {
        assertThat(Classification.of(Outcome.BLOQUEAR, OutcomeType.CHARGEBACK)).isEqualTo(Classification.VERDADEIRO_POSITIVO);
        assertThat(Classification.of(Outcome.REVISAR, OutcomeType.FRAUDE_CONFIRMADA)).isEqualTo(Classification.VERDADEIRO_POSITIVO);
    }

    @Test
    void fraudApprovedIsFalseNegative() {
        assertThat(Classification.of(Outcome.APROVAR, OutcomeType.CHARGEBACK)).isEqualTo(Classification.FALSO_NEGATIVO);
    }

    @Test
    void legitimateHeldIsFalsePositive() {
        assertThat(Classification.of(Outcome.BLOQUEAR, OutcomeType.LEGITIMA_CONFIRMADA)).isEqualTo(Classification.FALSO_POSITIVO);
        assertThat(Classification.of(Outcome.REVISAR, OutcomeType.LEGITIMA_CONFIRMADA)).isEqualTo(Classification.FALSO_POSITIVO);
    }

    @Test
    void legitimateApprovedIsTrueNegative() {
        assertThat(Classification.of(Outcome.APROVAR, OutcomeType.LEGITIMA_CONFIRMADA)).isEqualTo(Classification.VERDADEIRO_NEGATIVO);
    }
}
