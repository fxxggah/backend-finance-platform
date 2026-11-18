package com.finance.billing.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface BillingGateway {
    record Plan(String id, BigDecimal amount) { }
    record Checkout(String id, String url, Instant expiresAt) { @Override public String toString() { return "Checkout[omitido]"; } }
    record Subscription(String id, String status, boolean cancelAtPeriodEnd, Instant periodEnd) { }
    record Invoice(String id, String subscriptionId, String chargeId, BigDecimal amount, Instant periodStart,
            Instant periodEnd, boolean revoked, boolean reviewRequired) { }
    record Snapshot(String customerId, String reference, List<Subscription> subscriptions, List<Invoice> invoices) { }
    record Event(String id, String type, String resourceId) { }
    Plan plan();
    String createCustomer(String reference);
    Checkout checkout(String customerId, String reference, String operation, Instant expiresAt);
    String portal(String customerId);
    Snapshot snapshot(String customerId, String reference);
    String eventCustomer(Event event);
    Event verify(byte[] payload, String signature);
    void deleteCustomer(String customerId);
}
