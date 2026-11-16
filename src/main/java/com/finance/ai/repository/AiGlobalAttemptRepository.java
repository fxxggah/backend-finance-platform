package com.finance.ai.repository;

import com.finance.ai.entity.AiGlobalAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

/** Ledger global sem vínculo a usuário: não contém dado financeiro ou pessoal. */
public interface AiGlobalAttemptRepository extends JpaRepository<AiGlobalAttempt, Long> {
    long countByReservedAtGreaterThanAndReservedAtLessThanEqual(Instant after, Instant until);
    long countByReservedAtGreaterThanEqualAndReservedAtLessThan(Instant from, Instant until);
}
