package com.finance.ai.repository;

import com.finance.ai.entity.AiQuotaGuard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface AiQuotaGuardRepository extends JpaRepository<AiQuotaGuard, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guard from AiQuotaGuard guard where guard.id = 1")
    AiQuotaGuard lockGuard();
}
