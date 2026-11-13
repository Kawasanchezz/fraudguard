package com.fraudguard.feedback;

import com.fraudguard.evaluation.FraudDecision;
import com.fraudguard.evaluation.FraudDecisionRepository;
import com.fraudguard.evaluation.RuleHit;
import com.fraudguard.metrics.FraudMetrics;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutcomeService {

    private final FraudDecisionRepository decisions;
    private final DecisionOutcomeRepository outcomes;
    private final FraudMetrics metrics;

    public OutcomeService(FraudDecisionRepository decisions, DecisionOutcomeRepository outcomes, FraudMetrics metrics) {
        this.decisions = decisions;
        this.outcomes = outcomes;
        this.metrics = metrics;
    }

    public record Recorded(String transactionId, OutcomeType type, Classification classification, int rulesetVersion) {}

    /** Registra (ou corrige) o desfecho. Repetir a mesma chamada é seguro: não duplica nem recontabiliza. */
    @Transactional
    public Recorded record(String transactionId, OutcomeType type, Instant occurredAt) {
        FraudDecision decision = decisions.findByTransactionId(transactionId)
                .orElseThrow(UnknownDecisionException::new);
        var existing = outcomes.findByTransactionId(transactionId);
        boolean mudou = existing.isEmpty() || existing.get().getType() != type;
        if (existing.isPresent()) {
            existing.get().update(type, occurredAt);
        } else {
            outcomes.save(new DecisionOutcome(transactionId, type, occurredAt));
        }
        Classification c = Classification.of(decision.getOutcome(), type);
        if (mudou) {
            metrics.feedback(c, decision.getRulesetVersion());
            if (c == Classification.FALSO_POSITIVO || c == Classification.FALSO_NEGATIVO) {
                for (RuleHit hit : decision.getRulesFired()) {
                    metrics.feedbackRule(c, hit.code());
                }
            }
        }
        return new Recorded(transactionId, type, c, decision.getRulesetVersion());
    }

    public record VersionReport(int rulesetVersion, long verdadeirosPositivos, long falsosPositivos,
                                long falsosNegativos, long verdadeirosNegativos,
                                Double precisao, Double recall) {}

    /** Por versão de regras: assim dá para ver se um ajuste de peso melhorou ou piorou de verdade. */
    @Transactional(readOnly = true)
    public List<VersionReport> report() {
        Map<Integer, Map<Classification, Long>> porVersao = new TreeMap<>();
        for (var l : outcomes.contagens()) {
            porVersao.computeIfAbsent(l.getVersao(), v -> new EnumMap<>(Classification.class))
                    .merge(Classification.of(l.getDecisao(), l.getReal()), l.getTotal(), Long::sum);
        }
        return porVersao.entrySet().stream().map(e -> {
            var m = e.getValue();
            long tp = m.getOrDefault(Classification.VERDADEIRO_POSITIVO, 0L);
            long fp = m.getOrDefault(Classification.FALSO_POSITIVO, 0L);
            long fn = m.getOrDefault(Classification.FALSO_NEGATIVO, 0L);
            long tn = m.getOrDefault(Classification.VERDADEIRO_NEGATIVO, 0L);
            return new VersionReport(e.getKey(), tp, fp, fn, tn,
                    tp + fp == 0 ? null : (double) tp / (tp + fp),
                    tp + fn == 0 ? null : (double) tp / (tp + fn));
        }).toList();
    }
}
