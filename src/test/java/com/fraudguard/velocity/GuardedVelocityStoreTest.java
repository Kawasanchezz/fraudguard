package com.fraudguard.velocity;

import static com.fraudguard.rules.Fixtures.NOON_SP;
import static com.fraudguard.rules.Fixtures.tx;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudguard.metrics.FraudMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Circuit breaker: Redis falhando abre o circuito e as chamadas seguintes nem tocam o Redis. */
class GuardedVelocityStoreTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AtomicInteger calls = new AtomicInteger();

    private CircuitBreaker breaker() {
        return CircuitBreaker.of("t", CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1)).build());
    }

    @Test
    void failuresDegradeToUnavailableAndOpenTheCircuit() {
        VelocityStore broken = (t, a, w) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("redis down");
        };
        var cb = breaker();
        var store = new GuardedVelocityStore(broken, cb, new FraudMetrics(meters));

        for (int i = 0; i < 4; i++) {
            assertThat(store.observe(tx("10"), NOON_SP, Duration.ofMinutes(5)).available()).isFalse();
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(calls.get()).isEqualTo(4);

        // Com o circuito aberto o Redis nem é chamado.
        assertThat(store.observe(tx("10"), NOON_SP, Duration.ofMinutes(5)).available()).isFalse();
        assertThat(calls.get()).isEqualTo(4);

        assertThat(meters.get("fraud.cache.unavailable").tag("reason", "error").counter().count()).isEqualTo(4);
        assertThat(meters.get("fraud.cache.unavailable").tag("reason", "circuit_open").counter().count()).isEqualTo(1);
    }

    @Test
    void healthyDelegatePassesThrough() {
        var snap = VelocitySnapshot.of(2, true, false, false);
        var store = new GuardedVelocityStore((t, a, w) -> snap, breaker(), new FraudMetrics(meters));
        assertThat(store.observe(tx("10"), NOON_SP, Duration.ofMinutes(5))).isSameAs(snap);
    }
}
