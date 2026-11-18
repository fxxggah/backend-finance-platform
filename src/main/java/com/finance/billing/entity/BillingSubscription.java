package com.finance.billing.entity;
import com.finance.common.entity.BaseEntity;
import com.finance.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.math.BigDecimal;
@Entity @Table(name="billing_subscriptions") @Getter @Setter
public class BillingSubscription extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="user_id", nullable=false) private User user;
    @Column(nullable=false) private String stripeSubscriptionId;
    @Column(nullable=false, length=32) private String status;
    @Column(nullable=false) private boolean cancelAtPeriodEnd;
    private Instant currentPeriodEnd;
    @Override public boolean equals(Object other) { return this == other || other instanceof BillingSubscription value && id != null && id.equals(value.getId()); }
    @Override public int hashCode() { return getClass().hashCode(); }
    @Override public String toString() { return "BillingSubscription[omitido]"; }
}
