package com.fraudguard.metrics;

import com.fraudguard.evaluation.Outcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class FraudMetrics {

    private final MeterRegistry registry;

    public FraudMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    /** Histograma com bucket em 100 ms: dá para ler o p95 e o SLO direto no Grafana. */
    public void stop(Timer.Sample sample, String channel) {
        sample.stop(Timer.builder("fraud.evaluation.latency")
                .description("Latência da avaliação de fraude")
                .tag("channel", channel)
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(100))
                .register(registry));
    }

    public void decision(Outcome outcome, String channel) {
        registry.counter("fraud.decisions", "outcome", outcome.name(), "channel", channel).increment();
    }

    public void ruleFired(String code) {
        registry.counter("fraud.rule.fired", "rule", code).increment();
    }

    public void cacheUnavailable(String reason) {
        registry.counter("fraud.cache.unavailable", "reason", reason).increment();
    }

    public void failSafeActivated() {
        registry.counter("fraud.failsafe.activations").increment();
    }
}
