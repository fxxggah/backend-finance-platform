package com.finance.billing.service;

import com.finance.billing.config.BillingProperties;
import com.finance.billing.dto.BillingResponses;
import com.finance.billing.exception.BillingException;
import com.finance.billing.provider.BillingGateway;
import com.finance.user.service.UserDataDeletionService;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class BillingService {
    private final BillingStore store;
    private final BillingGateway gateway;
    private final BillingProperties properties;
    private final UserDataDeletionService deletion;
    private final Clock clock;
    public BillingService(BillingStore store, BillingGateway gateway, BillingProperties properties, UserDataDeletionService deletion, Clock clock) {
        this.store = store; this.gateway = gateway; this.properties = properties; this.deletion = deletion; this.clock = clock;
    }
    public BillingResponses.Plan plan(Long userId) {
        store.status(userId); var plan = gateway.plan(); return new BillingResponses.Plan(plan.amount(), "BRL", "MONTH", true);
    }
    public BillingResponses.Status status(Long userId) { return store.status(userId); }
    public BillingResponses.Session checkout(Long userId, String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) throw new BillingException(HttpStatus.BAD_REQUEST,
                "INVALID_IDEMPOTENCY_KEY", "Informe uma chave idempotente válida para iniciar o pagamento.");
        var claim = store.claim(userId, false); boolean success = false;
        try {
            claim = ensureCustomer(claim);
            store.apply(claim, gateway.snapshot(claim.customerId(), claim.reference()));
            var work = store.reserveCheckout(claim, hash(key), hash(properties.getPriceId() + "|" + properties.getFrontendOrigin()));
            String url = work.url();
            if (url == null) {
                var result = gateway.checkout(claim.customerId(), claim.reference(), work.operation(), work.expiresAt());
                store.checkoutReady(claim, result); url = result.url();
            }
            success = true; return new BillingResponses.Session(url);
        } finally { store.release(claim, success); }
    }
    public BillingResponses.Session portal(Long userId) {
        var claim = store.claim(userId, false); boolean success = false;
        try {
            if (claim.customerId() == null) throw new BillingException(HttpStatus.NOT_FOUND, "BILLING_CUSTOMER_NOT_FOUND", "Você ainda não possui uma assinatura para gerenciar.");
            String url = gateway.portal(claim.customerId()); success = true; return new BillingResponses.Session(url);
        } finally { store.release(claim, success); }
    }
    public void webhook(byte[] bytes, String signature) {
        var event = gateway.verify(bytes, signature);
        if (event != null) {
            try { store.record(event); }
            catch (org.springframework.dao.DataIntegrityViolationException ex) {
                // Uma entrega concorrente pode ter criado o mesmo ID. Reconsultar em nova transação.
                store.record(event);
            }
        }
    }
    public void synchronize(Long userId) {
        var claim = store.claim(userId, true); boolean success = false;
        try {
            claim = ensureCustomer(claim);
            if (claim.deleting()) {
                gateway.deleteCustomer(claim.customerId());
                deletion.delete(userId);
            } else store.apply(claim, gateway.snapshot(claim.customerId(), claim.reference()));
            success = true;
        } finally { store.release(claim, success); }
    }
    public void process(BillingStore.EventWork work) {
        boolean success = false;
        try {
            String customer = gateway.eventCustomer(work.event());
            Long userId = store.userForCustomer(customer);
            if (userId != null) synchronize(userId);
            success = true;
        } finally { store.finish(work, success); }
    }
    private BillingStore.Claim ensureCustomer(BillingStore.Claim claim) {
        if (claim.customerId() != null) return claim;
        // A Stripe pode remover chaves idempotentes após 24 h. Nunca repetir criação incerta além disso.
        if (!claim.customerStartedAt().plusSeconds(23 * 3600).isAfter(clock.instant())) throw BillingException.review();
        return store.bind(claim, gateway.createCustomer(claim.reference()));
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 indisponível"); }
    }
}
