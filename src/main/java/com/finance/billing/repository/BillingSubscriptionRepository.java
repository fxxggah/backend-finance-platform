package com.finance.billing.repository;
import com.finance.billing.entity.BillingSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.*;
public interface BillingSubscriptionRepository extends JpaRepository<BillingSubscription,Long> {
    List<BillingSubscription> findByUserIdOrderByIdDesc(Long userId, Pageable page);
    Optional<BillingSubscription> findByStripeSubscriptionIdAndUserId(String subscriptionId, Long userId);
    boolean existsByUserIdAndStatusNotIn(Long userId, Collection<String> statuses);
}
