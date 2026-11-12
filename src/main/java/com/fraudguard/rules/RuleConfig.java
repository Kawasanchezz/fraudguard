package com.fraudguard.rules;

import java.util.Map;

/** Regra ativa com seu peso e parâmetros, como resolvidos no conjunto de regras vigente. */
public record RuleConfig(String code, int weight, Map<String, Object> params) {}
