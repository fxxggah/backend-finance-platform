package com.finance.billing.entity;
import com.finance.common.entity.BaseEntity;
import com.finance.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.math.BigDecimal;
@Entity @Table(name="billing_customers") @Getter @Setter
public class BillingCustomer extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="user_id", nullable=false, unique=true) private User user;
    @Column(name="reference_uuid", nullable=false, length=36) private String reference;
    private String stripeCustomerId;
    @Column(nullable=false) private Instant customerStartedAt;
    @Column(length=64) private String checkoutKeyHash;
    @Column(length=36) private String checkoutOperation;
    private String checkoutId;
    @Column(length=2048) private String checkoutUrl;
    private Instant checkoutExpiresAt;
    @Column(length=64) private String checkoutFingerprint;
    @Column(length=36) private String syncToken;
    private Instant leaseUntil;
    @Column(nullable=false) private Instant nextSyncAt;
    @Column(nullable=false) private boolean deleting;
    @Override public boolean equals(Object other) { return this == other || other instanceof BillingCustomer value && id != null && id.equals(value.getId()); }
    @Override public int hashCode() { return getClass().hashCode(); }
    @Override public String toString() { return "BillingCustomer[omitido]"; }
}
