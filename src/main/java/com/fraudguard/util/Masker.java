package com.fraudguard.util;

/** LGPD: ids nunca vão inteiros para os logs; ids curtos somem por completo e caracteres de controle são removidos (evita forjar linhas de log). */
public final class Masker {
    private Masker() {}

    public static String mask(String value) {
        if (value == null) {
            return "null";
        }
        String clean = value.replaceAll("\\p{Cntrl}", "");
        if (clean.length() <= 8) {
            return "****";
        }
        return clean.substring(0, 2) + "***" + clean.substring(clean.length() - 2);
    }
}
