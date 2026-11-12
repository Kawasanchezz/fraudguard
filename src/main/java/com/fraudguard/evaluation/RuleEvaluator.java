package com.fraudguard.evaluation;

import com.fraudguard.config.FraudProperties;
import com.fraudguard.rules.FraudRule;
import com.fraudguard.rules.RuleConfig;
import com.fraudguard.rules.RuleContext;
import com.fraudguard.rules.RuleSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Soma os pesos das regras que dispararam (limitado a 100) e traduz o score em decisão.
 * Não conhece nenhuma regra concreta: recebe todas as implementações de FraudRule.
 */
@Component
public class RuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RuleEvaluator.class);
    static final int MAX_SCORE = 100;

    private final Map<String, FraudRule> rulesByCode;
    private final int reviewThreshold;
    private final int blockThreshold;

    @Autowired
    public RuleEvaluator(List<FraudRule> rules, FraudProperties props) {
        this(rules, props.thresholds().review(), props.thresholds().block());
    }

    public RuleEvaluator(List<FraudRule> rules, int reviewThreshold, int blockThreshold) {
        this.rulesByCode = rules.stream().collect(Collectors.toMap(FraudRule::code, Function.identity()));
        this.reviewThreshold = reviewThreshold;
        this.blockThreshold = blockThreshold;
    }

    public EvaluationResult evaluate(RuleContext ctx, RuleSet ruleSet) {
        List<RuleHit> hits = new ArrayList<>();
        int score = 0;
        for (RuleConfig config : ruleSet.rules()) {
            FraudRule rule = rulesByCode.get(config.code());
            if (rule == null) {
                log.warn("Regra configurada sem implementação: {}", config.code());
                continue;
            }
            Optional<String> reason;
            try {
                reason = rule.evaluate(ctx, config.params());
            } catch (RuntimeException e) {
                // Uma regra defeituosa não pode derrubar a avaliação das demais.
                log.error("Regra {} falhou e foi ignorada: {}", config.code(), e.toString());
                continue;
            }
            if (reason.isPresent()) {
                hits.add(new RuleHit(config.code(), config.weight(), reason.get()));
                score += config.weight();
            }
        }
        score = Math.min(MAX_SCORE, score);
        return new EvaluationResult(ctx.tx().transactionId(), outcomeFor(score), score,
                List.copyOf(hits), ruleSet.version());
    }

    Outcome outcomeFor(int score) {
        if (score >= blockThreshold) {
            return Outcome.BLOQUEAR;
        }
        return score >= reviewThreshold ? Outcome.REVISAR : Outcome.APROVAR;
    }
}
