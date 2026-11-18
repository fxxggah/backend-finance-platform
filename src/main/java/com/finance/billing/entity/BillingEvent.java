package com.finance.billing.entity;
import com.finance.common.entity.BaseEntity;
import com.finance.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.math.BigDecimal;
@Entity @Table(name="billing_events") @Getter @Setter
public class BillingEvent extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String stripeEventId;
    @Column(nullable=false, length=100) private String eventType;
    @Column(nullable=false) private String resourceId;
    @Column(nullable=false, length=16) private String status;
    @Column(nullable=false) private int attempts;
    @Column(nullable=false) private Instant nextAttemptAt;
    private Instant leaseUntil;
    @Column(length=36) private String token;
    @Override public boolean equals(Object other) { return this == other || other instanceof BillingEvent value && id != null && id.equals(value.getId()); }
    @Override public int hashCode() { return getClass().hashCode(); }
    @Override public String toString() { return "BillingEvent[omitido]"; }
}
