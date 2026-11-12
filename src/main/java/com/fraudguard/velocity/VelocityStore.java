package com.fraudguard.velocity;

import com.fraudguard.evaluation.TransactionRequest;
import java.time.Duration;
import java.time.Instant;

public interface VelocityStore {

    /**
     * Registra a transação nas janelas/conjuntos do cliente e devolve o estado resultante.
     * A contagem recente já inclui a própria transação.
     */
    VelocitySnapshot observe(TransactionRequest tx, Instant at, Duration window);
}
