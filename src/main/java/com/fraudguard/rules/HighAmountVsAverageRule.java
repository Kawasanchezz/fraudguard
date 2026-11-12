package com.fraudguard.rules;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Valor muito acima da média do cliente. Param: multiplier (padrão 5). */
@Component
public class HighAmountVsAverageRule implements FraudRule {

    public static final String CODE = "HIGH_AMOUNT_VS_AVERAGE";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<String> evaluate(RuleContext ctx, Map<String, Object> params) {
        if (ctx.profile() == null || ctx.profile().getAvgAmount() == null
                || ctx.profile().getAvgAmount().signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal multiplier = Params.decimal(params, "multiplier", BigDecimal.valueOf(5));
        BigDecimal limit = ctx.profile().getAvgAmount().multiply(multiplier);
        if (ctx.tx().amount().compareTo(limit) > 0) {
            return Optional.of("valor acima de " + multiplier.stripTrailingZeros().toPlainString()
                    + "x a média do cliente");
        }
        return Optional.empty();
    }
}
