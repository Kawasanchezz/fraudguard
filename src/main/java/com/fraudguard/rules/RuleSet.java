package com.fraudguard.rules;

import java.time.Duration;
import java.util.List;

/** Conjunto de regras ATIVAS numa versão. A versão vai em cada decisão (auditoria). */
public record RuleSet(int version, List<RuleConfig> rules) {

    private static final int DEFAULT_WINDOW_SECONDS = 300;

    /** Janela de velocidade lida da regra VELOCITY (mesmo desativada, os contadores continuam). */
    public Duration velocityWindow() {
        return rules.stream()
                .filter(r -> VelocityRule.CODE.equals(r.code()))
                .findFirst()
                .map(r -> Duration.ofSeconds(Params.integer(r.params(), "windowSeconds", DEFAULT_WINDOW_SECONDS)))
                .orElse(Duration.ofSeconds(DEFAULT_WINDOW_SECONDS));
    }
}
