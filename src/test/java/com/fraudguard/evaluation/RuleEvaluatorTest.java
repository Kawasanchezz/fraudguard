package com.fraudguard.evaluation;

import static com.fraudguard.rules.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudguard.rules.FraudRule;
import com.fraudguard.rules.RuleConfig;
import com.fraudguard.rules.RuleContext;
import com.fraudguard.rules.RuleSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RuleEvaluatorTest {

    /** Regra de teste: dispara sempre, ou nunca. Mostra que o avaliador não conhece regras concretas. */
    private record Stub(String code, boolean fires) implements FraudRule {
        public Optional<String> evaluate(RuleContext c, Map<String, Object> p) {
            return fires ? Optional.of(code + " disparou") : Optional.empty();
        }
    }

    private final RuleEvaluator evaluator = new RuleEvaluator(
            List.of(new Stub("A", true), new Stub("B", true), new Stub("C", true), new Stub("D", false)), 40, 80);

    private static RuleConfig cfg(String code, int weight) {
        return new RuleConfig(code, weight, Map.of());
    }

    static Stream<Arguments> combinations() {
        return Stream.of(
                Arguments.of(List.of(), 0, Outcome.APROVAR),
                Arguments.of(List.of(cfg("D", 90)), 0, Outcome.APROVAR),                 // regra que não dispara
                Arguments.of(List.of(cfg("A", 39)), 39, Outcome.APROVAR),               // logo abaixo do limite
                Arguments.of(List.of(cfg("A", 40)), 40, Outcome.REVISAR),               // limite inclusivo
                Arguments.of(List.of(cfg("A", 30), cfg("B", 30)), 60, Outcome.REVISAR),
                Arguments.of(List.of(cfg("A", 79)), 79, Outcome.REVISAR),
                Arguments.of(List.of(cfg("A", 80)), 80, Outcome.BLOQUEAR),
                Arguments.of(List.of(cfg("A", 40), cfg("B", 40), cfg("D", 50)), 80, Outcome.BLOQUEAR),
                Arguments.of(List.of(cfg("A", 70), cfg("B", 70), cfg("C", 70)), 100, Outcome.BLOQUEAR), // teto 100
                Arguments.of(List.of(cfg("A", 0)), 0, Outcome.APROVAR),                 // peso zero
                Arguments.of(List.of(cfg("Z", 99)), 0, Outcome.APROVAR)                 // sem implementação
        );
    }

    @ParameterizedTest
    @MethodSource("combinations")
    void sumsWeightsOfFiredRules(List<RuleConfig> configs, int expectedScore, Outcome expected) {
        var result = evaluator.evaluate(ctx("100", null, quiet(), NOON_SP), new RuleSet(7, configs));
        assertThat(result.score()).isEqualTo(expectedScore);
        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(result.rulesetVersion()).isEqualTo(7);
        assertThat(result.transactionId()).isEqualTo("tx-1");
    }

    @Test
    void reportsOnlyFiredRulesWithReasonAndWeight() {
        var result = evaluator.evaluate(ctx("100", null, quiet(), NOON_SP),
                new RuleSet(1, List.of(cfg("A", 25), cfg("D", 50))));
        assertThat(result.reasons()).containsExactly(new RuleHit("A", 25, "A disparou"));
    }

    @Test
    void faultyRuleIsSkippedAndOthersStillCount() {
        FraudRule broken = new FraudRule() {
            public String code() { return "BROKEN"; }
            public Optional<String> evaluate(RuleContext c, Map<String, Object> p) { throw new IllegalStateException("boom"); }
        };
        var ev = new RuleEvaluator(List.of(broken, new Stub("A", true)), 40, 80);
        var result = ev.evaluate(ctx("100", null, quiet(), NOON_SP),
                new RuleSet(1, List.of(cfg("BROKEN", 90), cfg("A", 40))));
        assertThat(result.score()).isEqualTo(40);
        assertThat(result.outcome()).isEqualTo(Outcome.REVISAR);
    }
}
