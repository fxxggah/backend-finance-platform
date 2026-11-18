package com.finance.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public final class BillingResponses {
    private BillingResponses() { }
    @io.swagger.v3.oas.annotations.media.Schema(name="BillingPlanResponse")
    public record Plan(BigDecimal amount, String currency, String interval, boolean testMode) { }
    @io.swagger.v3.oas.annotations.media.Schema(name="BillingStatusResponse")
    public record Status(String status, Instant paidUntil, Instant manualProExpiresAt, Instant effectiveProExpiresAt,
            boolean cancelAtPeriodEnd, boolean canCheckout, boolean canManage, boolean reviewRequired, boolean testMode) {
        @Override public String toString() { return "BillingStatus[conteúdo omitido]"; }
    }
    @io.swagger.v3.oas.annotations.media.Schema(name="BillingSessionResponse")
    public record Session(String url) { @Override public String toString() { return "BillingSession[conteúdo omitido]"; } }
}
