package com.fraudguard.rules;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Muitas transações em poucos minutos. Params: maxTransactions (5) e windowSeconds (300).
 * A janela em si é aplicada no Redis (ver VelocityStore); a regra compara a contagem.
 */
@Component
public class VelocityRule implements FraudRule {

    public static final String CODE = "VELOCITY";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<String> evaluate(RuleContext ctx, Map<String, Object> params) {
        int max = Params.integer(params, "maxTransactions", 5);
        long count = ctx.velocity().recentCount();
        if (count > max) {
            int window = Params.integer(params, "windowSeconds", 300);
            return Optional.of(count + " transações em " + window + "s (limite " + max + ")");
        }
        return Optional.empty();
    }
}
