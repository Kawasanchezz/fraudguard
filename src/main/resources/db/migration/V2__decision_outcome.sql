-- Desfecho real (fechamento do ciclo): o que aconteceu depois da decisão, vindo do cliente/adquirente.
-- Um desfecho por transação (o mais recente vale). Sem dado pessoal: só o id da transação e o tipo.
CREATE TABLE decision_outcome (
    id             BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL,
    type           VARCHAR(20) NOT NULL,
    occurred_at    TIMESTAMPTZ,
    recorded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_decision_outcome_tx UNIQUE (transaction_id)
);
