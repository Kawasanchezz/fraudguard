package com.fraudguard.cases;

import com.fraudguard.evaluation.FraudDecision;
import com.fraudguard.evaluation.FraudDecisionRepository;
import com.fraudguard.evaluation.RuleHit;
import com.fraudguard.util.Masker;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    public record CaseView(Long caseId, FraudCase.Status status, String transactionId, int score,
                           List<RuleHit> reasons, Instant createdAt, String analyst, String note,
                           Instant resolvedAt) {}

    private final FraudCaseRepository cases;
    private final FraudDecisionRepository decisions;

    public CaseService(FraudCaseRepository cases, FraudDecisionRepository decisions) {
        this.cases = cases;
        this.decisions = decisions;
    }

    @Transactional(readOnly = true)
    public List<CaseView> list(FraudCase.Status status) {
        return cases.findTop100ByStatusOrderByCreatedAtAsc(status).stream().map(this::view).toList();
    }

    @Transactional
    public CaseView resolve(Long id, FraudCase.Status finalStatus, String analyst, String note) {
        FraudCase c = cases.lockById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caso não encontrado"));
        if (c.getStatus() != FraudCase.Status.ABERTO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Caso já resolvido por " + c.getAnalyst());
        }
        c.resolve(finalStatus, analyst, note);
        log.info("caso {} resolvido como {} por {}", id, finalStatus, Masker.mask(analyst));
        return view(c);
    }

    private CaseView view(FraudCase c) {
        FraudDecision d = decisions.findById(c.getDecisionId()).orElseThrow();
        return new CaseView(c.getId(), c.getStatus(), d.getTransactionId(), d.getScore(), d.getRulesFired(),
                c.getCreatedAt(), c.getAnalyst(), c.getNote(), c.getResolvedAt());
    }
}
