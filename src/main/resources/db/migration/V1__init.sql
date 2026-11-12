-- Regras versionadas: alterar peso/params insere uma nova linha com version maior (histórico imutável).
CREATE TABLE fraud_rule (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(60)  NOT NULL,
    weight     INT          NOT NULL CHECK (weight BETWEEN 0 AND 100),
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    params     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    version    INT          NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_fraud_rule_code_version UNIQUE (code, version)
);

-- LGPD: a decisão guarda só o id da transação, score, motivos e versão. Nenhum dado pessoal.
CREATE TABLE fraud_decision (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  VARCHAR(64) NOT NULL,
    score           INT         NOT NULL,
    outcome         VARCHAR(10) NOT NULL,
    rules_fired     JSONB       NOT NULL,
    ruleset_version INT         NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Único: garante idempotência (avaliação síncrona + evento assíncrono da mesma transação).
CREATE UNIQUE INDEX ux_fraud_decision_transaction ON fraud_decision (transaction_id);
CREATE INDEX ix_fraud_decision_outcome_created ON fraud_decision (outcome, created_at);

CREATE TABLE fraud_case (
    id          BIGSERIAL PRIMARY KEY,
    decision_id BIGINT      NOT NULL REFERENCES fraud_decision (id),
    status      VARCHAR(12) NOT NULL,
    analyst     VARCHAR(60),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX ix_fraud_case_open ON fraud_case (created_at) WHERE status = 'ABERTO';

CREATE TABLE customer_profile (
    customer_id VARCHAR(64) PRIMARY KEY,
    avg_amount  NUMERIC(18, 2),
    usual_hours VARCHAR(5),          -- "HH-HH", ex.: "06-23"; nulo = usar padrão da regra
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO fraud_rule (code, weight, enabled, params, version) VALUES
  ('HIGH_AMOUNT_VS_AVERAGE',     40, TRUE, '{"multiplier": 5}', 1),
  ('VELOCITY',                   40, TRUE, '{"maxTransactions": 5, "windowSeconds": 300}', 1),
  ('UNUSUAL_HOUR',               15, TRUE, '{"startHour": 0, "endHour": 6, "zone": "America/Sao_Paulo"}', 1),
  ('NEW_DEVICE_OR_LOCATION',     25, TRUE, '{}', 1),
  ('NEW_RECIPIENT_HIGH_AMOUNT',  30, TRUE, '{"minAmount": 2000}', 1);
