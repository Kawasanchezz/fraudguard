package com.fraudguard.evaluation;

/** Uma regra que disparou: guardada na decisão para explicabilidade e auditoria. */
public record RuleHit(String code, int weight, String reason) {}
