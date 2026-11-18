package com.finance.billing.repository;
import com.finance.billing.entity.BillingInvoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.*;
public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice,Long> {
    Optional<BillingInvoice> findByStripeInvoiceIdAndUserId(String invoiceId, Long userId);
    @Query("select max(i.periodEnd) from BillingInvoice i where i.user.id = :userId and i.revoked = false and i.periodStart <= :now and i.periodEnd > :now")
    Instant paidUntil(@Param("userId") Long userId, @Param("now") Instant now);
    boolean existsByUserIdAndReviewRequiredTrue(Long userId);
}
