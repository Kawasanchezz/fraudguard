package com.fraudguard.rules;

import java.math.BigDecimal;
import java.util.Map;

/** Leitura tolerante dos parâmetros jsonb (número pode chegar como Integer, Long ou Double). */
public final class Params {
    private Params() {}

    public static int integer(Map<String, Object> p, String key, int def) {
        Object v = p == null ? null : p.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public static BigDecimal decimal(Map<String, Object> p, String key, BigDecimal def) {
        Object v = p == null ? null : p.get(key);
        return v instanceof Number n ? new BigDecimal(n.toString()) : def;
    }

    public static String string(Map<String, Object> p, String key, String def) {
        Object v = p == null ? null : p.get(key);
        return v instanceof String s && !s.isBlank() ? s : def;
    }
}
