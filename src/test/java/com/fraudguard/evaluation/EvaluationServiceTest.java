package com.fraudguard.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fraudguard.config.FraudProperties;
import com.fraudguard.metrics.FraudMetrics;
import com.fraudguard.profile.CustomerProfileRepository;
import com.fraudguard.profile.ProfileUpdater;
import com.fraudguard.rules.RuleSet;
import com.fraudguard.rules.RuleSetProvider;
import com.fraudguard.velocity.VelocitySnapshot;
import com.fraudguard.velocity.VelocityStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class EvaluationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private final RuleSetProvider ruleSets = mock(RuleSetProvider.class);
    private final CustomerProfileRepository profiles = mock(CustomerProfileRepository.class);
    private final VelocityStore velocity = mock(VelocityStore.class);
    private final FraudDecisionRepository decisions = mock(FraudDecisionRepository.class);
    private final DecisionRecorder recorder = mock(DecisionRecorder.class);
    private final ProfileUpdater updater = mock(ProfileUpdater.class);

    private EvaluationService service() {
        var props = new FraudProperties(null, null, null, Duration.ofHours(24), null);
        when(ruleSets.current()).thenReturn(new RuleSet(1, List.of()));
        when(profiles.findById(any())).thenReturn(Optional.empty());
        when(decisions.findByTransactionId(any())).thenReturn(Optional.empty());
        when(velocity.observe(any(), any(), any())).thenReturn(VelocitySnapshot.of(1, false, false, false));
        return new EvaluationService(ruleSets, profiles, velocity, new RuleEvaluator(List.of(), 40, 80),
                new FailSafePolicy(new BigDecimal("5000"), 40), decisions, recorder, updater,
                new FraudMetrics(new SimpleMeterRegistry()), Clock.fixed(NOW, ZoneOffset.UTC), props);
    }

    private static TransactionRequest tx(Instant occurredAt) {
        return new TransactionRequest("t-1", "c-1", new BigDecimal("10"), TransactionRequest.Channel.PIX,
                "d", "l", "r", occurredAt);
    }

    @Test
    void occurredAtOutsideTheToleranceIsRejectedBeforeAnyStateIsTouched() {
        var service = service();
        for (Instant bad : List.of(NOW.plus(Duration.ofHours(25)), NOW.minus(Duration.ofHours(25)),
                Instant.parse("+999999999-12-31T00:00:00Z"), Instant.parse("-999999999-01-01T00:00:00Z"))) {
            assertThatThrownBy(() -> service.evaluate(tx(bad), "sync")).isInstanceOf(InvalidTransactionException.class);
        }
        verify(velocity, never()).observe(any(), any(), any());
        verify(recorder, never()).record(any());
    }

    @Test
    void occurredAtInsideTheToleranceAndMissingOnesAreAccepted() {
        var service = service();
        assertThat(service.evaluate(tx(NOW.minus(Duration.ofHours(23))), "sync").outcome()).isEqualTo(Outcome.APROVAR);
        assertThat(service.evaluate(tx(null), "sync").outcome()).isEqualTo(Outcome.APROVAR);
    }

    @Test
    void onlyApprovedTransactionsTeachTheProfile() {
        var approved = service();
        approved.evaluate(tx(null), "sync");
        verify(updater).learn(ArgumentMatchers.any());

        var review = new RuleEvaluator(List.of(new com.fraudguard.rules.VelocityRule()), 1, 80);
        when(ruleSets.current()).thenReturn(new RuleSet(1,
                List.of(new com.fraudguard.rules.RuleConfig("VELOCITY", 50, java.util.Map.of("maxTransactions", 1)))));
        when(velocity.observe(any(), any(), any())).thenReturn(VelocitySnapshot.of(9, false, false, false));
        var suspicious = new EvaluationService(ruleSets, profiles, velocity, review,
                new FailSafePolicy(new BigDecimal("5000"), 40), decisions, recorder, updater,
                new FraudMetrics(new SimpleMeterRegistry()), Clock.fixed(NOW, ZoneOffset.UTC),
                new FraudProperties(null, null, null, Duration.ofHours(24), null));
        var tx = new TransactionRequest("t-2", "c-1", new BigDecimal("10"), TransactionRequest.Channel.PIX, null, null, null, null);
        assertThat(suspicious.evaluate(tx, "sync").outcome()).isEqualTo(Outcome.REVISAR);
        verify(updater, org.mockito.Mockito.times(1)).learn(ArgumentMatchers.any());
    }
}
