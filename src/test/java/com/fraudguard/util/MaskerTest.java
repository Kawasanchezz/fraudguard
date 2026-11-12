package com.fraudguard.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaskerTest {
    @Test
    void masksIdentifiers() {
        assertThat(Masker.mask("customer-12345")).isEqualTo("cu***45").doesNotContain("12345");
        assertThat(Masker.mask("abcd")).isEqualTo("****");
        assertThat(Masker.mask(null)).isEqualTo("null");
    }
}
