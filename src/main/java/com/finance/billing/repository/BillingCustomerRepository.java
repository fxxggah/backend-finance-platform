package com.finance.billing.repository;
import com.finance.billing.entity.BillingCustomer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.*;
public interface BillingCustomerRepository extends JpaRepository<BillingCustomer,Long> {
    Optional<BillingCustomer> findByUserId(Long userId);
    Optional<BillingCustomer> findByStripeCustomerId(String customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from BillingCustomer c where c.user.id = :userId")
    Optional<BillingCustomer> lockByUserId(@Param("userId") Long userId);
    @Query("select c.user.id from BillingCustomer c where c.nextSyncAt <= :now and (c.leaseUntil is null or c.leaseUntil <= :now) order by c.nextSyncAt, c.id")
    List<Long> dueUsers(@Param("now") Instant now, Pageable page);
}
