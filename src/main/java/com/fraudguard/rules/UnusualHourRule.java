package com.fraudguard.rules;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Horário incomum. Usa a faixa habitual do cliente (usual_hours) quando existe; senão a faixa
 * noturna dos params: startHour (0, inclusivo), endHour (6, exclusivo), zone (America/Sao_Paulo).
 */
@Component
public class UnusualHourRule implements FraudRule {

    public static final String CODE = "UNUSUAL_HOUR";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<String> evaluate(RuleContext ctx, Map<String, Object> params) {
        ZoneId zone = ZoneId.of(Params.string(params, "zone", "America/Sao_Paulo"));
        int hour = ctx.at().atZone(zone).getHour();

        if (ctx.profile() != null) {
            Optional<Boolean> usual = ctx.profile().isUsualHour(hour);
            if (usual.isPresent()) {
                return usual.get() ? Optional.empty()
                        : Optional.of("horário " + hour + "h fora da faixa habitual do cliente");
            }
        }
        int start = Params.integer(params, "startHour", 0);
        int end = Params.integer(params, "endHour", 6);
        boolean night = start <= end ? (hour >= start && hour < end) : (hour >= start || hour < end);
        return night ? Optional.of("transação às " + hour + "h (madrugada)") : Optional.empty();
    }
}
