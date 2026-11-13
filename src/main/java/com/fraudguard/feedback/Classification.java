package com.fraudguard.feedback;

import com.fraudguard.evaluation.Outcome;

/** Compara a decisão original com o desfecho real. */
public enum Classification {
    VERDADEIRO_POSITIVO, FALSO_POSITIVO, FALSO_NEGATIVO, VERDADEIRO_NEGATIVO;

    /** "Positivo" = o motor segurou a transação (REVISAR ou BLOQUEAR). */
    public static Classification of(Outcome decisao, OutcomeType real) {
        boolean seguro = decisao != Outcome.APROVAR;
        if (real.isFraude()) {
            return seguro ? VERDADEIRO_POSITIVO : FALSO_NEGATIVO;
        }
        return seguro ? FALSO_POSITIVO : VERDADEIRO_NEGATIVO;
    }
}
