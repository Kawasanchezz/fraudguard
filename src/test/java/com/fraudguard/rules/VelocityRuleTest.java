package com.fraudguard.rules;

import static com.fraudguard.rules.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudguard.velocity.VelocitySnapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VelocityRuleTest {

    private final VelocityRule rule = new VelocityRule();

    @ParameterizedTest
    @CsvSource({"1,false", "5,false", "6,true", "50,true"})
    void defaultLimitIsFive(long count, boolean fires) {
        var v = VelocitySnapshot.of(count, false, false, false);
        assertThat(rule.evaluate(ctx("10", null, v, NOON_SP), Map.of()).isPresent()).isEqualTo(fires);
    }

    @Test
    void respectsConfiguredLimit() {
        var v = VelocitySnapshot.of(3, false, false, false);
        assertThat(rule.evaluate(ctx("10", null, v, NOON_SP), Map.of("maxTransactions", 2, "windowSeconds", 60)))
                .hasValueSatisfying(s -> assertThat(s).contains("3 transações em 60s"));
    }

    @Test
    void unavailableCacheNeverFires() {
        assertThat(rule.evaluate(ctx("10", null, VelocitySnapshot.unavailable(), NOON_SP), Map.of())).isEmpty();
    }

    @Test
    void exposesCode() {
        assertThat(rule.code()).isEqualTo("VELOCITY");
    }
}
