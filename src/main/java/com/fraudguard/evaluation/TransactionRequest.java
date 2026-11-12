package com.fraudguard.evaluation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento de transação vindo dos Projetos 1 e 2.
 * LGPD: customerId deve ser um identificador interno pseudonimizado (nunca CPF/e-mail);
 * deviceId, location e recipientId são usados só como hash no Redis.
 */
public record TransactionRequest(
        @NotBlank @Size(max = 64) String transactionId,
        @NotBlank @Size(max = 64) String customerId,
        @NotNull @Positive @DecimalMax("1000000000") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull Channel channel,
        @Size(max = 128) String deviceId,
        @Size(max = 64) String location,
        @Size(max = 128) String recipientId,
        Instant occurredAt) {

    public enum Channel { PIX, CARTAO, TRANSFERENCIA }
}
