package com.fraudguard.config;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Corpo em chunks que passou do limite chega aqui como erro de leitura: responde 413 em vez de 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Void> unreadableBody(HttpMessageNotReadableException e) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        boolean tooLarge = root instanceof BodyTooLargeException;
        return ResponseEntity.status(tooLarge ? HttpStatus.CONTENT_TOO_LARGE : HttpStatus.BAD_REQUEST).build();
    }
}
