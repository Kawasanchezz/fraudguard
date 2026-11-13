package com.fraudguard.feedback;

public class UnknownDecisionException extends RuntimeException {
    public UnknownDecisionException() {
        super("sem decisão para a transação");
    }
}
