package com.fraudguard.evaluation;

import com.fraudguard.config.FraudProperties;
import com.fraudguard.metrics.FraudMetrics;
import com.fraudguard.profile.CustomerProfile;
import com.fraudguard.profile.CustomerProfileRepository;
import com.fraudguard.profile.ProfileUpdater;
import com.fraudguard.rules.RuleContext;
import com.fraudguard.rules.RuleSet;
import com.fraudguard.rules.RuleSetProvider;
import com.fraudguard.util.Masker;
import com.fraudguard.velocity.VelocitySnapshot;
import com.fraudguard.velocity.VelocityStore;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final RuleSetProvider ruleSets;
    private final CustomerProfileRepository profiles;
    private final VelocityStore velocity;
    private final RuleEvaluator evaluator;
    private final FailSafePolicy failSafe;
    private final FraudDecisionRepository decisions;
    private final DecisionRecorder recorder;
    private final ProfileUpdater profileUpdater;
    private final FraudMetrics metrics;
    private final Clock clock;
    private final Duration maxClockSkew;

    public EvaluationService(RuleSetProvider ruleSets, CustomerProfileRepository profiles,
                             VelocityStore velocity, RuleEvaluator evaluator, FailSafePolicy failSafe,
                             FraudDecisionRepository decisions, DecisionRecorder recorder,
                             ProfileUpdater profileUpdater, FraudMetrics metrics, Clock clock,
                             FraudProperties props) {
        this.ruleSets = ruleSets;
        this.profiles = profiles;
        this.velocity = velocity;
        this.evaluator = evaluator;
        this.failSafe = failSafe;
        this.decisions = decisions;
        this.recorder = recorder;
        this.profileUpdater = profileUpdater;
        this.metrics = metrics;
        this.clock = clock;
        this.maxClockSkew = props.clockSkew();
    }

    /** @param channel "sync" (HTTP) ou "async" (fila), usado só em métricas. */
    public EvaluationResult evaluate(TransactionRequest tx, String channel) {
        Timer.Sample sample = metrics.start();

        // Idempotência: mesma transação (ex.: chamada síncrona + evento da fila) = mesma decisão.
        Optional<FraudDecision> existing = decisions.findByTransactionId(tx.transactionId());
        if (existing.isPresent()) {
            metrics.stop(sample, channel);
            return EvaluationResult.from(existing.get());
        }

        EvaluationResult result = compute(tx);
        try {
            recorder.record(result);
        } catch (DataIntegrityViolationException duplicate) {
            // Corrida entre duas avaliações simultâneas da mesma transação: vale a que gravou primeiro.
            metrics.stop(sample, channel);
            return decisions.findByTransactionId(tx.transactionId())
                    .map(EvaluationResult::from)
                    .orElseThrow(() -> duplicate);
        }

        result.reasons().forEach(h -> metrics.ruleFired(h.code()));
        metrics.decision(result.outcome(), channel);
        if (result.outcome() == Outcome.APROVAR) {
            profileUpdater.learn(tx); // só o aprovado ensina o perfil: evita envenenar a média com casos suspeitos
        }
        metrics.stop(sample, channel);

        log.info("decisão tx={} cliente={} outcome={} score={} regras={}",
                Masker.mask(tx.transactionId()), Masker.mask(tx.customerId()),
                result.outcome(), result.score(), result.reasons().stream().map(RuleHit::code).toList());
        return result;
    }

    /** occurredAt vem do chamador: fora da tolerância permitiria burlar a janela do Redis ou estourar a conversão para milissegundos. */
    private Instant eventTime(TransactionRequest tx) {
        Instant now = clock.instant();
        if (tx.occurredAt() == null) {
            return now;
        }
        if (Duration.between(now, tx.occurredAt()).abs().compareTo(maxClockSkew) > 0) {
            throw new InvalidTransactionException("occurredAt fora da janela permitida");
        }
        return tx.occurredAt();
    }

    private EvaluationResult compute(TransactionRequest tx) {
        RuleSet ruleSet = ruleSets.current();
        CustomerProfile profile = profiles.findById(tx.customerId()).orElse(null);
        Instant at = eventTime(tx);

        VelocitySnapshot snapshot = velocity.observe(tx, at, ruleSet.velocityWindow());
        EvaluationResult result = evaluator.evaluate(new RuleContext(tx, profile, snapshot, at), ruleSet);

        if (!snapshot.available()) {
            metrics.failSafeActivated();
            result = failSafe.apply(result, tx.amount());
        }
        return result;
    }
}
