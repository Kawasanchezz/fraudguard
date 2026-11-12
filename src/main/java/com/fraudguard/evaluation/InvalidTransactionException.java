package com.fraudguard.evaluation;

public class InvalidTransactionException extends IllegalArgumentException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
