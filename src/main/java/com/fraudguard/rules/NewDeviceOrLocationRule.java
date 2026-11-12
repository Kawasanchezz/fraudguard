package com.fraudguard.rules;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Dispositivo ou localização nunca vistos para o cliente (o primeiro valor é só linha de base). */
@Component
public class NewDeviceOrLocationRule implements FraudRule {

    public static final String CODE = "NEW_DEVICE_OR_LOCATION";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<String> evaluate(RuleContext ctx, Map<String, Object> params) {
        boolean device = ctx.velocity().newDevice();
        boolean location = ctx.velocity().newLocation();
        if (device && location) {
            return Optional.of("dispositivo e localização novos");
        }
        if (device) {
            return Optional.of("dispositivo novo");
        }
        if (location) {
            return Optional.of("localização nova");
        }
        return Optional.empty();
    }
}
