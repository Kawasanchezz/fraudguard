package com.fraudguard.config;

import java.io.IOException;

public class BodyTooLargeException extends IOException {

    BodyTooLargeException(long limit) {
        super("Corpo da requisição acima de " + limit + " bytes");
    }
}
