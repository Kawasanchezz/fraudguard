package com.fraudguard.util;

/** LGPD: identificadores nunca vão inteiros para os logs. */
public final class Masker {
    private Masker() {}

    public static String mask(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
