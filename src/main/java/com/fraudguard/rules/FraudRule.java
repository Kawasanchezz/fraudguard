package com.fraudguard.rules;

import java.util.Map;
import java.util.Optional;

/**
 * Strategy: cada regra é independente e só conhece o contexto e seus próprios parâmetros.
 * Nova regra = nova classe @Component + uma linha em fraud_rule; nada mais muda (Open/Closed).
 * O peso não é responsabilidade da regra: vem da configuração versionada.
 */
public interface FraudRule {

    String code();

    /** @return o motivo legível se a regra disparou; vazio caso contrário. */
    Optional<String> evaluate(RuleContext ctx, Map<String, Object> params);
}
