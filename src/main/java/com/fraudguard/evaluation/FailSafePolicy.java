package com.fraudguard.evaluation;

import com.fraudguard.config.FraudProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Política quando o Redis está indisponível (decisão documentada no README):
 *  - as regras que não dependem de cache continuam valendo;
 *  - se a decisão sairia APROVAR e o valor é alto (>= failsafe.high-amount), escalona para REVISAR;
 *  - valores baixos seguem APROVAR: preferimos atrito zero a travar o cliente por falha nossa;
 *  - BLOQUEAR e REVISAR nunca são rebaixados.
 */
@Component
public class FailSafePolicy {

    public static final String CODE = "FAILSAFE_CACHE_UNAVAILABLE";

    private final BigDecimal highAmount;
    private final int reviewThreshold;

    @Autowired
    public FailSafePolicy(FraudProperties props) {
        this(props.failsafe().highAmount(), props.thresholds().review());
    }

    public FailSafePolicy(BigDecimal highAmount, int reviewThreshold) {
        this.highAmount = highAmount;
        this.reviewThreshold = reviewThreshold;
    }

    public EvaluationResult apply(EvaluationResult result, BigDecimal amount) {
        boolean high = amount.compareTo(highAmount) >= 0;
        String reason = high
                ? "Redis indisponível: valor alto enviado para revisão"
                : "Redis indisponível: valor baixo aprovado sem regras de velocidade/dispositivo";
        List<RuleHit> reasons = new ArrayList<>(result.reasons());
        reasons.add(new RuleHit(CODE, 0, reason));

        Outcome outcome = result.outcome();
        int score = result.score();
        if (high && outcome == Outcome.APROVAR) {
            outcome = Outcome.REVISAR;
            score = Math.max(score, reviewThreshold);
        }
        return new EvaluationResult(result.transactionId(), outcome, score, List.copyOf(reasons),
                result.rulesetVersion());
    }
}
