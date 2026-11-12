package com.fraudguard.rules;

import static com.fraudguard.rules.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HighAmountVsAverageRuleTest {

    private final HighAmountVsAverageRule rule = new HighAmountVsAverageRule();

    @Test
    void firesWhenAboveMultiplier() {
        var r = rule.evaluate(ctx("501", profile("100", null), quiet(), NOON_SP), Map.of());
        assertThat(r).isPresent();
        assertThat(r.get()).contains("5x");
    }

    @Test
    void doesNotFireExactlyAtLimit() {
        assertThat(rule.evaluate(ctx("500", profile("100", null), quiet(), NOON_SP), Map.of())).isEmpty();
    }

    @Test
    void respectsConfiguredMultiplier() {
        assertThat(rule.evaluate(ctx("250", profile("100", null), quiet(), NOON_SP), Map.of("multiplier", 2)))
                .isPresent();
        assertThat(rule.evaluate(ctx("250", profile("100", null), quiet(), NOON_SP), Map.of("multiplier", 2.5)))
                .isEmpty();
    }

    @Test
    void noBaselineMeansNoFire() {
        assertThat(rule.evaluate(ctx("999999", null, quiet(), NOON_SP), Map.of())).isEmpty();
        assertThat(rule.evaluate(ctx("999999", profile(null, null), quiet(), NOON_SP), Map.of())).isEmpty();
        assertThat(rule.evaluate(ctx("999999", profile("0", null), quiet(), NOON_SP), Map.of())).isEmpty();
    }

    @Test
    void exposesCode() {
        assertThat(rule.code()).isEqualTo("HIGH_AMOUNT_VS_AVERAGE");
    }
}
