package com.finance.billing.entity;
import com.finance.common.entity.BaseEntity;
import com.finance.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.math.BigDecimal;
@Entity @Table(name="billing_invoices") @Getter @Setter
public class BillingInvoice extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="user_id", nullable=false) private User user;
    @Column(nullable=false) private String stripeInvoiceId;
    @Column(nullable=false) private String stripeSubscriptionId;
    @Column(nullable=false) private String stripeChargeId;
    @Column(nullable=false, precision=19, scale=2) private BigDecimal amount;
    @Column(nullable=false, length=3) private String currency;
    @Column(nullable=false) private Instant periodStart;
    @Column(nullable=false) private Instant periodEnd;
    @Column(nullable=false) private boolean revoked;
    @Column(nullable=false) private boolean reviewRequired;
    @Override public boolean equals(Object other) { return this == other || other instanceof BillingInvoice value && id != null && id.equals(value.getId()); }
    @Override public int hashCode() { return getClass().hashCode(); }
    @Override public String toString() { return "BillingInvoice[omitido]"; }
}
