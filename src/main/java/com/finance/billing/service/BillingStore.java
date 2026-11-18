package com.finance.billing.service;

import com.finance.billing.dto.BillingResponses;
import com.finance.billing.entity.*;
import com.finance.billing.repository.*;
import com.finance.billing.provider.BillingGateway;
import com.finance.billing.exception.BillingException;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import com.finance.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

/** Transações locais curtas. Nenhuma chamada HTTP nesta classe. */
@Service @RequiredArgsConstructor
public class BillingStore {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final BillingCustomerRepository customers;
    private final BillingSubscriptionRepository subscriptions;
    private final BillingInvoiceRepository invoices;
    private final BillingEventRepository events;
    private final Clock clock;
    private static final Set<String> TERMINAL = Set.of("canceled", "incomplete_expired");

    public record Claim(Long userId, String reference, String customerId, String token, boolean deleting, Instant customerStartedAt) {
        @Override public String toString() { return "BillingClaim[omitido]"; }
    }
    public record CheckoutWork(String operation, Instant expiresAt, String url) { @Override public String toString() { return "CheckoutWork[omitido]"; } }
    public record EventWork(Long id, String token, BillingGateway.Event event) { }

    @Transactional
    public Claim claim(Long userId, boolean allowDeleting) {
        User user = users.lockById(userId).orElseThrow(BillingStore::notFound);
        var value = customers.lockByUserId(userId).orElse(null);
        if (!user.isActive() && !(allowDeleting && value != null && value.isDeleting())) throw notFound();
        Instant now = now();
        if (value == null) {
            value = new BillingCustomer(); value.setUser(user); value.setReference(UUID.randomUUID().toString());
            value.setCustomerStartedAt(now); value.setNextSyncAt(now); customers.save(value);
        }
        if (value.getLeaseUntil() != null && value.getLeaseUntil().isAfter(now)) throw BillingException.busy();
        value.setSyncToken(UUID.randomUUID().toString()); value.setLeaseUntil(now.plusSeconds(180));
        return new Claim(userId, value.getReference(), value.getStripeCustomerId(), value.getSyncToken(), value.isDeleting(), value.getCustomerStartedAt());
    }
    @Transactional
    public Claim bind(Claim claim, String customerId) {
        var value = fenced(claim, true);
        if (value.getStripeCustomerId() != null && !value.getStripeCustomerId().equals(customerId)) throw BillingException.invalid();
        value.setStripeCustomerId(customerId);
        return new Claim(claim.userId(), claim.reference(), customerId, claim.token(), value.isDeleting(), claim.customerStartedAt());
    }
    @Transactional
    public void apply(Claim claim, BillingGateway.Snapshot snapshot) {
        var value = fenced(claim, false);
        if (!claim.customerId().equals(snapshot.customerId()) || !claim.reference().equals(snapshot.reference())) throw BillingException.invalid();
        for (var item : snapshot.subscriptions()) {
            var sub = subscriptions.findByStripeSubscriptionIdAndUserId(item.id(), claim.userId()).orElseGet(BillingSubscription::new);
            sub.setUser(value.getUser()); sub.setStripeSubscriptionId(item.id()); sub.setStatus(item.status());
            sub.setCancelAtPeriodEnd(item.cancelAtPeriodEnd()); sub.setCurrentPeriodEnd(item.periodEnd()); subscriptions.save(sub);
        }
        var known = snapshot.subscriptions().stream().map(BillingGateway.Subscription::id).collect(java.util.stream.Collectors.toSet());
        for (var item : snapshot.invoices()) {
            if (!known.contains(item.subscriptionId()) || item.amount() == null || item.amount().signum() <= 0
                    || item.periodStart() == null || !item.periodEnd().isAfter(item.periodStart())) throw BillingException.invalid();
            var invoice = invoices.findByStripeInvoiceIdAndUserId(item.id(), claim.userId()).orElseGet(BillingInvoice::new);
            invoice.setUser(value.getUser()); invoice.setStripeInvoiceId(item.id()); invoice.setStripeSubscriptionId(item.subscriptionId());
            invoice.setStripeChargeId(item.chargeId()); invoice.setAmount(item.amount()); invoice.setCurrency("BRL");
            invoice.setPeriodStart(item.periodStart()); invoice.setPeriodEnd(item.periodEnd());
            invoice.setRevoked(item.revoked()); invoice.setReviewRequired(item.reviewRequired()); invoices.save(invoice);
        }
        // Um vínculo removido do snapshot completo não pode continuar impedindo nova contratação.
        for (var sub : subscriptions.findByUserIdOrderByIdDesc(claim.userId(), PageRequest.of(0,100))) {
            if (!known.contains(sub.getStripeSubscriptionId())) sub.setStatus("canceled");
        }
    }
    @Transactional
    public CheckoutWork reserveCheckout(Claim claim, String keyHash, String fingerprint) {
        var value = fenced(claim, false);
        if (subscriptions.existsByUserIdAndStatusNotIn(claim.userId(), TERMINAL)) throw new BillingException(HttpStatus.CONFLICT, "SUBSCRIPTION_EXISTS", "Você já possui uma assinatura. Use Gerenciar assinatura.");
        if (value.getCheckoutOperation() != null) {
            if (keyHash.equals(value.getCheckoutKeyHash())) {
                if (!fingerprint.equals(value.getCheckoutFingerprint())) throw BillingException.review();
                if (!value.getCheckoutExpiresAt().isAfter(now())) throw new BillingException(HttpStatus.CONFLICT, "CHECKOUT_EXPIRED", "O checkout expirou. Inicie uma nova tentativa.");
                return new CheckoutWork(value.getCheckoutOperation(), value.getCheckoutExpiresAt(), value.getCheckoutUrl());
            }
            if (value.getCheckoutExpiresAt().isAfter(now())) {
                if (value.getCheckoutUrl() != null && fingerprint.equals(value.getCheckoutFingerprint()))
                    return new CheckoutWork(value.getCheckoutOperation(), value.getCheckoutExpiresAt(), value.getCheckoutUrl());
                throw BillingException.busy();
            }
        }
        value.setCheckoutKeyHash(keyHash); value.setCheckoutFingerprint(fingerprint); value.setCheckoutOperation(UUID.randomUUID().toString());
        value.setCheckoutExpiresAt(now().plusSeconds(2100)); value.setCheckoutId(null); value.setCheckoutUrl(null);
        return new CheckoutWork(value.getCheckoutOperation(), value.getCheckoutExpiresAt(), null);
    }
    @Transactional
    public void checkoutReady(Claim claim, BillingGateway.Checkout checkout) {
        var value = fenced(claim, false);
        if (!value.getCheckoutExpiresAt().equals(checkout.expiresAt())) throw BillingException.invalid();
        value.setCheckoutId(checkout.id()); value.setCheckoutUrl(checkout.url());
    }
    @Transactional
    public void release(Claim claim, boolean success) {
        customers.lockByUserId(claim.userId()).filter(c -> claim.token().equals(c.getSyncToken())).ifPresent(value -> {
            value.setSyncToken(null); value.setLeaseUntil(null); value.setNextSyncAt(now().plusSeconds(success ? 300 : 30));
        });
    }
    @Transactional(readOnly=true)
    public BillingResponses.Status status(Long userId) {
        User user = users.findById(userId).filter(User::isActive).orElseThrow(BillingStore::notFound);
        var customer = customers.findByUserId(userId).orElse(null);
        var list = subscriptions.findByUserIdOrderByIdDesc(userId, PageRequest.of(0,100));
        var latest = list.stream().filter(s -> !TERMINAL.contains(s.getStatus())).findFirst().orElse(list.isEmpty() ? null : list.getFirst());
        Instant paid = invoices.paidUntil(userId, now());
        boolean checkout = list.stream().allMatch(s -> TERMINAL.contains(s.getStatus()))
                && (customer == null || customer.getCheckoutExpiresAt() == null || !customer.getCheckoutExpiresAt().isAfter(now()) || customer.getCheckoutUrl() != null);
        return new BillingResponses.Status(latest == null ? "NONE" : latest.getStatus().toUpperCase(Locale.ROOT), paid, user.getProExpiresAt(),
                ProAccessService.max(user.getProExpiresAt(), paid), latest != null && latest.isCancelAtPeriodEnd(), checkout,
                latest != null && customer != null && customer.getStripeCustomerId() != null, invoices.existsByUserIdAndReviewRequiredTrue(userId), true);
    }
    @Transactional(readOnly=true)
    public Long userForCustomer(String customerId) { return customers.findByStripeCustomerId(customerId).map(c -> c.getUser().getId()).orElse(null); }
    @Transactional(readOnly=true)
    public List<Long> dueUsers() { return customers.dueUsers(now(), PageRequest.of(0,10)); }
    @Transactional
    public void record(BillingGateway.Event event) {
        var previous = events.findByStripeEventId(event.id()).orElse(null);
        if (previous != null) {
            if (!event.type().equals(previous.getEventType()) || !event.resourceId().equals(previous.getResourceId())) throw BillingException.invalid();
            return;
        }
        var value = new BillingEvent(); value.setStripeEventId(event.id()); value.setEventType(event.type());
        value.setResourceId(event.resourceId()); value.setStatus("PENDING"); value.setNextAttemptAt(now()); events.saveAndFlush(value);
    }
    @Transactional(readOnly=true)
    public List<Long> dueEvents() { return events.dueEvents(now(), PageRequest.of(0,25)); }
    @Transactional
    public EventWork claimEvent(Long id) {
        var value = events.lockById(id).orElseThrow(BillingException::busy);
        if ("DONE".equals(value.getStatus()) || "PROCESSING".equals(value.getStatus()) && value.getLeaseUntil().isAfter(now())) return null;
        value.setStatus("PROCESSING"); value.setToken(UUID.randomUUID().toString()); value.setLeaseUntil(now().plusSeconds(240));
        value.setAttempts(value.getAttempts()+1);
        return new EventWork(id, value.getToken(), new BillingGateway.Event(value.getStripeEventId(), value.getEventType(), value.getResourceId()));
    }
    @Transactional
    public void finish(EventWork work, boolean success) {
        var value = events.lockById(work.id()).orElseThrow(BillingException::busy);
        if (!work.token().equals(value.getToken())) return;
        value.setStatus(success ? "DONE" : "PENDING"); value.setLeaseUntil(null); value.setToken(null);
        value.setNextAttemptAt(now().plusSeconds(Math.min(3600, 30L << Math.min(6, value.getAttempts()))));
    }
    @Transactional
    public boolean beginDeletion(Long userId) {
        User user = users.lockById(userId).orElseThrow(BillingStore::notFound);
        var value = customers.lockByUserId(userId).orElse(null);
        if (value == null) return false;
        user.setActive(false); refreshTokens.deleteAllByUserId(userId);
        value.setDeleting(true); value.setSyncToken(null); value.setLeaseUntil(null); value.setNextSyncAt(now()); return true;
    }
    private BillingCustomer fenced(Claim claim, boolean allowDeleting) {
        User user = users.lockById(claim.userId()).orElseThrow(BillingStore::notFound);
        var value = customers.lockByUserId(claim.userId()).orElseThrow(BillingStore::notFound);
        if (!claim.token().equals(value.getSyncToken()) || value.getLeaseUntil() == null || !value.getLeaseUntil().isAfter(now())) throw BillingException.busy();
        if ((!user.isActive() || value.isDeleting()) && !allowDeleting) throw notFound();
        return value;
    }
    private Instant now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS); }
    private static BillingException notFound() { return new BillingException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado."); }
}
