package com.fraudguard.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaskerTest {
    @Test
    void masksIdentifiers() {
        assertThat(Masker.mask("customer-12345")).isEqualTo("cu***45").doesNotContain("12345");
        assertThat(Masker.mask("abcd")).isEqualTo("****");
        assertThat(Masker.mask(null)).isEqualTo("null");
        assertThat(Masker.mask("12345678")).isEqualTo("****");
    }

    @Test
    void stripsControlCharactersSoLogLinesCannotBeForged() {
        String masked = Masker.mask("ab\r\nFAKE LOG LINE\r\ncd");
        assertThat(masked).doesNotContain("\n").doesNotContain("\r");
    }
}
