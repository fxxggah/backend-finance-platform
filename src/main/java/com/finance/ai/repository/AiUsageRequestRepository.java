package com.finance.ai.repository;

import com.finance.ai.entity.AiUsageRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface AiUsageRequestRepository extends JpaRepository<AiUsageRequest, Long> {
    Optional<AiUsageRequest> findByUserIdAndKeyHash(Long userId, String keyHash);
    Optional<AiUsageRequest> findByIdAndUserId(Long id, Long userId);
    long countByUserIdAndStatusAndSuccessDay(Long userId, AiUsageRequest.Status status, LocalDate day);
    boolean existsByUserIdAndStatusAndLeaseUntilGreaterThan(Long userId, AiUsageRequest.Status status, Instant now);

    @Modifying
    @Query("update AiUsageRequest request set request.status = :failed, request.updatedAt = :now "
            + "where request.user.id = :userId and request.status = :processing and request.leaseUntil <= :now")
    int expire(@Param("userId") Long userId, @Param("now") Instant now,
               @Param("processing") AiUsageRequest.Status processing, @Param("failed") AiUsageRequest.Status failed);
}
