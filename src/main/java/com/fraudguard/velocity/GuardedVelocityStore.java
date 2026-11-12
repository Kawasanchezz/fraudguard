package com.fraudguard.velocity;

import com.fraudguard.evaluation.TransactionRequest;
import com.fraudguard.metrics.FraudMetrics;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Protege o endpoint síncrono contra o Redis: timeout curto + circuit breaker.
 * Com o circuito aberto não gastamos nem os 100 ms de timeout; devolvemos "indisponível"
 * e o avaliador aplica a política fail-safe (ver README).
 */
@Primary
@Component
public class GuardedVelocityStore implements VelocityStore {

    private static final Logger log = LoggerFactory.getLogger(GuardedVelocityStore.class);

    private final VelocityStore delegate;
    private final CircuitBreaker breaker;
    private final FraudMetrics metrics;

    public GuardedVelocityStore(@Qualifier("redisVelocityStore") VelocityStore delegate,
                                CircuitBreaker velocityCircuitBreaker,
                                FraudMetrics metrics) {
        this.delegate = delegate;
        this.breaker = velocityCircuitBreaker;
        this.metrics = metrics;
    }

    @Override
    public VelocitySnapshot observe(TransactionRequest tx, Instant at, Duration window) {
        try {
            return breaker.executeSupplier(() -> delegate.observe(tx, at, window));
        } catch (CallNotPermittedException e) {
            metrics.cacheUnavailable("circuit_open");
        } catch (RuntimeException e) {
            log.warn("Redis indisponível ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            metrics.cacheUnavailable("error");
        }
        return VelocitySnapshot.unavailable();
    }
}
