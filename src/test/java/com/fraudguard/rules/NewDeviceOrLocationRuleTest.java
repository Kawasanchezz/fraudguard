package com.fraudguard.rules;

import static com.fraudguard.rules.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudguard.velocity.VelocitySnapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NewDeviceOrLocationRuleTest {

    private final NewDeviceOrLocationRule rule = new NewDeviceOrLocationRule();

    @ParameterizedTest
    @CsvSource({
            "false,false,",
            "true,false,dispositivo novo",
            "false,true,localização nova",
            "true,true,dispositivo e localização novos"
    })
    void reasonsPerCombination(boolean device, boolean location, String expected) {
        var v = VelocitySnapshot.of(1, device, location, false);
        var r = rule.evaluate(ctx("10", null, v, NOON_SP), Map.of());
        if (expected == null) {
            assertThat(r).isEmpty();
        } else {
            assertThat(r).contains(expected);
        }
    }

    @Test
    void unavailableCacheNeverFires() {
        assertThat(rule.evaluate(ctx("10", null, VelocitySnapshot.unavailable(), NOON_SP), Map.of())).isEmpty();
    }

    @Test
    void exposesCode() {
        assertThat(rule.code()).isEqualTo("NEW_DEVICE_OR_LOCATION");
    }
}
