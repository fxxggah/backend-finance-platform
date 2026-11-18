package com.finance.billing.repository;
import com.finance.billing.entity.BillingEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.*;
public interface BillingEventRepository extends JpaRepository<BillingEvent,Long> {
    Optional<BillingEvent> findByStripeEventId(String eventId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from BillingEvent e where e.id = :id")
    Optional<BillingEvent> lockById(@Param("id") Long id);
    @Query("select e.id from BillingEvent e where (e.status = 'PENDING' and e.nextAttemptAt <= :now) or (e.status = 'PROCESSING' and e.leaseUntil <= :now) order by e.nextAttemptAt, e.id")
    List<Long> dueEvents(@Param("now") Instant now, Pageable page);
}
