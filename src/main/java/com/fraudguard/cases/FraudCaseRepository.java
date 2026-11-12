package com.fraudguard.cases;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FraudCaseRepository extends JpaRepository<FraudCase, Long> {

    List<FraudCase> findTop100ByStatusOrderByCreatedAtAsc(FraudCase.Status status);

    /** Trava a linha: dois analistas decidindo o mesmo caso não se atropelam. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FraudCase c where c.id = :id")
    Optional<FraudCase> lockById(@Param("id") Long id);
}
