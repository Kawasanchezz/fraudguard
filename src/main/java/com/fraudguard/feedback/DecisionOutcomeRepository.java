package com.fraudguard.feedback;

import com.fraudguard.evaluation.Outcome;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DecisionOutcomeRepository extends JpaRepository<DecisionOutcome, Long> {

    Optional<DecisionOutcome> findByTransactionId(String transactionId);

    /** Contagem por versão de regras, decisão original e desfecho real. */
    @Query("""
            select d.rulesetVersion as versao, d.outcome as decisao, o.type as real, count(o) as total
            from DecisionOutcome o, FraudDecision d
            where d.transactionId = o.transactionId
            group by d.rulesetVersion, d.outcome, o.type
            """)
    List<Linha> contagens();

    interface Linha {
        Integer getVersao();
        Outcome getDecisao();
        OutcomeType getReal();
        Long getTotal();
    }
}
