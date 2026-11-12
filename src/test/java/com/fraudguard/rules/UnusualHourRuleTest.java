package com.fraudguard.rules;

import static com.fraudguard.rules.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnusualHourRuleTest {

    private final UnusualHourRule rule = new UnusualHourRule();

    // Horário de Brasília (UTC-3, sem horário de verão): 03:00 UTC = 00:00 SP.
    @ParameterizedTest
    @CsvSource({
            "2026-01-15T02:59:00Z,true",   // 23:59 SP -> normal
            "2026-01-15T03:00:00Z,false",  // 00:00 SP -> madrugada (início inclusivo)
            "2026-01-15T08:59:00Z,false",  // 05:59 SP -> madrugada
            "2026-01-15T09:00:00Z,true"    // 06:00 SP -> fim exclusivo
    })
    void defaultNightWindowIsMidnightToSix(String instant, boolean normal) {
        var r = rule.evaluate(ctx("10", null, quiet(), Instant.parse(instant)), Map.of());
        assertThat(r.isEmpty()).isEqualTo(normal);
    }

    @Test
    void usesCustomerUsualHoursWhenPresent() {
        var p = profile("100", "08-18");
        // 12:00 SP dentro da faixa
        assertThat(rule.evaluate(ctx("10", p, quiet(), NOON_SP), Map.of())).isEmpty();
        // 20:00 SP (23:00Z) fora da faixa habitual, mesmo não sendo madrugada
        assertThat(rule.evaluate(ctx("10", p, quiet(), Instant.parse("2026-01-15T23:00:00Z")), Map.of()))
                .hasValueSatisfying(s -> assertThat(s).contains("fora da faixa habitual"));
    }

    @Test
    void usualHoursCanCrossMidnight() {
        var p = profile("100", "22-06");
        assertThat(rule.evaluate(ctx("10", p, quiet(), Instant.parse("2026-01-15T04:00:00Z")), Map.of())).isEmpty(); // 01:00 SP
        assertThat(rule.evaluate(ctx("10", p, quiet(), NOON_SP), Map.of())).isPresent();
    }

    @Test
    void malformedUsualHoursFallBackToNightWindow() {
        var p = profile("100", "abc");
        assertThat(rule.evaluate(ctx("10", p, quiet(), NOON_SP), Map.of())).isEmpty();
        assertThat(rule.evaluate(ctx("10", p, quiet(), Instant.parse("2026-01-15T05:00:00Z")), Map.of())).isPresent();
    }

    @Test
    void windowThatCrossesMidnightInParams() {
        var params = Map.<String, Object>of("startHour", 22, "endHour", 6);
        assertThat(rule.evaluate(ctx("10", null, quiet(), Instant.parse("2026-01-16T01:30:00Z")), params)).isPresent(); // 22:30 SP
        assertThat(rule.evaluate(ctx("10", null, quiet(), NOON_SP), params)).isEmpty();
    }

    @Test
    void respectsZoneParam() {
        var utc = Map.<String, Object>of("zone", "UTC");
        assertThat(rule.evaluate(ctx("10", null, quiet(), Instant.parse("2026-01-15T03:00:00Z")), utc)).isPresent();
    }

    @Test
    void exposesCode() {
        assertThat(rule.code()).isEqualTo("UNUSUAL_HOUR");
    }
}
