package com.fraudguard.rules;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Destinatário novo com valor alto. Param: minAmount (padrão 2000, inclusivo). */
@Component
public class NewRecipientHighAmountRule implements FraudRule {

    public static final String CODE = "NEW_RECIPIENT_HIGH_AMOUNT";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<String> evaluate(RuleContext ctx, Map<String, Object> params) {
        BigDecimal min = Params.decimal(params, "minAmount", BigDecimal.valueOf(2000));
        if (ctx.velocity().newRecipient() && ctx.tx().amount().compareTo(min) >= 0) {
            return Optional.of("destinatário novo com valor >= " + min.stripTrailingZeros().toPlainString());
        }
        return Optional.empty();
    }
}
