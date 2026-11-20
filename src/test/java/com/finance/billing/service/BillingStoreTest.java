package com.finance.billing.service;

import com.finance.auth.repository.RefreshTokenRepository;
import com.finance.billing.entity.BillingCustomer;
import com.finance.billing.entity.BillingEvent;
import com.finance.billing.exception.BillingException;
import com.finance.billing.provider.BillingGateway;
import com.finance.billing.repository.BillingCustomerRepository;
import com.finance.billing.repository.BillingEventRepository;
import com.finance.billing.repository.BillingInvoiceRepository;
import com.finance.billing.repository.BillingSubscriptionRepository;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingStoreTest {
    private static final long USER = 7L;
    private static final long EVENT_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final String URL = "https://checkout.example.test/previous";
    private static final BillingStore.Claim CLAIM = new BillingStore.Claim(USER, "ref", "cus_test", "current", false, NOW);
    private static final BillingGateway.Event EVENT = new BillingGateway.Event("evt_test", "invoice.paid", "in_test");

    @Mock private UserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private BillingCustomerRepository customers;
    @Mock private BillingSubscriptionRepository subscriptions;
    @Mock private BillingInvoiceRepository invoices;
    @Mock private BillingEventRepository events;
    private BillingStore store;

    @BeforeEach
    void setUp() {
        store = new BillingStore(users, refreshTokens, customers, subscriptions, invoices, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsReplayWhenCheckoutExpiresAtTheCurrentInstant() {
        var customer = existingCheckout(NOW);

        assertThatThrownBy(() -> store.reserveCheckout(CLAIM, "old-key", "fingerprint"))
                .isInstanceOfSatisfying(BillingException.class,
                        error -> assertThat(error.code()).isEqualTo("CHECKOUT_EXPIRED"));
        assertThat(customer.getCheckoutOperation()).isEqualTo("old-operation");
        assertThat(customer.getCheckoutUrl()).isEqualTo(URL);
        assertThat(customer.getCheckoutExpiresAt()).isEqualTo(NOW);
    }

    @Test
    void newKeyAfterExpirationStartsFreshCheckoutAndClearsOldUrl() {
        var customer = existingCheckout(NOW);

        var work = store.reserveCheckout(CLAIM, "new-key", "fingerprint");

        assertThat(work.operation()).isNotBlank().isNotEqualTo("old-operation");
        assertThat(work.expiresAt()).isEqualTo(NOW.plusSeconds(2100));
        assertThat(work.url()).isNull();
        assertThat(customer.getCheckoutOperation()).isEqualTo(work.operation());
        assertThat(customer.getCheckoutKeyHash()).isEqualTo("new-key");
        assertThat(customer.getCheckoutId()).isNull();
        assertThat(customer.getCheckoutUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsCheckoutResultAfterClaimIsSupersededOrExpired(boolean superseded) {
        var customer = existingCheckout(NOW.plusSeconds(100));
        if (superseded) customer.setSyncToken("newer-claim");
        else customer.setLeaseUntil(NOW);

        assertThatThrownBy(() -> store.checkoutReady(CLAIM,
                new BillingGateway.Checkout("cs_new", "https://checkout.example.test/other", NOW.plusSeconds(100))))
                .isInstanceOfSatisfying(BillingException.class,
                        error -> assertThat(error.code()).isEqualTo("BILLING_OPERATION_IN_PROGRESS"));
        assertThat(customer.getCheckoutId()).isEqualTo("cs_old");
        assertThat(customer.getCheckoutUrl()).isEqualTo(URL);
    }

    @Test
    void duplicateEventWithIdenticalDataDoesNotWriteAgain() {
        var previous = processingEvent(NOW.plusSeconds(40));
        when(events.findByStripeEventId("evt_test")).thenReturn(Optional.of(previous));

        store.record(EVENT);

        verify(events, never()).saveAndFlush(any());
        assertThat(previous.getStatus()).isEqualTo("PROCESSING");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void duplicateEventWithDifferentTypeOrResourceIsRejected(boolean differentType) {
        var previous = processingEvent(NOW.plusSeconds(40));
        when(events.findByStripeEventId("evt_test")).thenReturn(Optional.of(previous));
        var conflicting = new BillingGateway.Event("evt_test",
                differentType ? "invoice.payment_failed" : "invoice.paid", differentType ? "in_test" : "in_other");

        assertThatThrownBy(() -> store.record(conflicting))
                .isInstanceOfSatisfying(BillingException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_STRIPE_RESPONSE"));
        verify(events, never()).saveAndFlush(any());
        assertThat(previous.getEventType()).isEqualTo("invoice.paid");
        assertThat(previous.getResourceId()).isEqualTo("in_test");
    }

    @Test
    void liveEventLeasePreventsDuplicateClaim() {
        var event = processingEvent(NOW.plusSeconds(1));
        when(events.lockById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThat(store.claimEvent(EVENT_ID)).isNull();

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getToken()).isEqualTo("old-attempt");
    }

    @Test
    void expiredLeaseCanBeReclaimedAndOldCompletionCannotChangeState() {
        var event = processingEvent(NOW);
        when(events.lockById(EVENT_ID)).thenReturn(Optional.of(event));
        var oldWork = new BillingStore.EventWork(EVENT_ID, "old-attempt", EVENT);

        var work = store.claimEvent(EVENT_ID);
        assertThat(work.token()).isNotBlank().isNotEqualTo(oldWork.token());
        assertThat(event.getLeaseUntil()).isEqualTo(NOW.plusSeconds(240));
        assertThat(event.getAttempts()).isEqualTo(2);

        store.finish(oldWork, true);
        assertThat(event.getStatus()).isEqualTo("PROCESSING");
        assertThat(event.getToken()).isEqualTo(work.token());
        assertThat(event.getLeaseUntil()).isEqualTo(NOW.plusSeconds(240));

        store.finish(work, false);
        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getToken()).isNull();
        assertThat(event.getLeaseUntil()).isNull();
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void beginningDeletionImmediatelyDeactivatesAccountAndReleasesExistingClaim() {
        var user = activeUser();
        var customer = new BillingCustomer();
        customer.setSyncToken("current");
        customer.setLeaseUntil(NOW.plusSeconds(15));
        when(users.lockById(USER)).thenReturn(Optional.of(user));
        when(customers.lockByUserId(USER)).thenReturn(Optional.of(customer));

        assertThat(store.beginDeletion(USER)).isTrue();

        assertThat(user.isActive()).isFalse();
        verify(refreshTokens).deleteAllByUserId(USER);
        assertThat(customer.isDeleting()).isTrue();
        assertThat(customer.getSyncToken()).isNull();
        assertThat(customer.getLeaseUntil()).isNull();
        assertThat(customer.getNextSyncAt()).isEqualTo(NOW);
    }

    private BillingCustomer existingCheckout(Instant expiry) {
        var customer = new BillingCustomer();
        customer.setCheckoutKeyHash("old-key");
        customer.setCheckoutFingerprint("fingerprint");
        customer.setCheckoutOperation("old-operation");
        customer.setCheckoutExpiresAt(expiry);
        customer.setCheckoutId("cs_old");
        customer.setCheckoutUrl(URL);
        customer.setSyncToken("current");
        customer.setLeaseUntil(NOW.plusSeconds(180));
        when(users.lockById(USER)).thenReturn(Optional.of(activeUser()));
        when(customers.lockByUserId(USER)).thenReturn(Optional.of(customer));
        return customer;
    }

    private static User activeUser() {
        return new User("synthetic", "test@example.test", "Test", null);
    }

    private static BillingEvent processingEvent(Instant leaseUntil) {
        var event = new BillingEvent();
        event.setStripeEventId("evt_test");
        event.setEventType("invoice.paid");
        event.setResourceId("in_test");
        event.setStatus("PROCESSING");
        event.setAttempts(1);
        event.setToken("old-attempt");
        event.setLeaseUntil(leaseUntil);
        return event;
    }
}
