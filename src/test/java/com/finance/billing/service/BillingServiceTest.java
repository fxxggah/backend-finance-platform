package com.finance.billing.service;

import com.finance.billing.config.BillingProperties;
import com.finance.billing.exception.BillingException;
import com.finance.billing.provider.BillingGateway;
import com.finance.user.service.UserDataDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {
    private static final long USER = 7L;
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final String KEY = "checkout-test-key";
    private static final String URL = "https://checkout.example.test/session";
    private static final BillingGateway.Event EVENT = new BillingGateway.Event("evt_test", "invoice.paid", "in_test");

    @Mock private BillingStore store;
    @Mock private BillingGateway gateway;
    @Mock private UserDataDeletionService deletion;
    private BillingService service;

    @BeforeEach
    void setUp() {
        var properties = new BillingProperties();
        properties.setPriceId("price_test");
        properties.setFrontendOrigin("https://fin.example.test");
        service = new BillingService(store, gateway, properties, deletion, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsMalformedCheckoutKeyBeforeCallingStoreOrProvider() {
        assertThatThrownBy(() -> service.checkout(USER, "short"))
                .isInstanceOfSatisfying(BillingException.class, error ->
                        assertThat(error.code()).isEqualTo("INVALID_IDEMPOTENCY_KEY"));
        verifyNoInteractions(store, gateway);
    }

    @Test
    void reusesHostedCheckoutAfterReconciliationWithoutCreatingAnother() {
        var claim = claim(false);
        var snapshot = new BillingGateway.Snapshot("cus_test", "ref", List.of(), List.of());
        when(store.claim(USER, false)).thenReturn(claim);
        when(gateway.snapshot("cus_test", "ref")).thenReturn(snapshot);
        when(store.reserveCheckout(eq(claim), anyString(), anyString()))
                .thenReturn(new BillingStore.CheckoutWork("operation", NOW.plusSeconds(900), URL));

        assertThat(service.checkout(USER, KEY).url()).isEqualTo(URL);

        var order = inOrder(store, gateway);
        order.verify(store).claim(USER, false);
        order.verify(gateway).snapshot("cus_test", "ref");
        order.verify(store).apply(claim, snapshot);
        order.verify(store).reserveCheckout(eq(claim), matches("[0-9a-f]{64}"), matches("[0-9a-f]{64}"));
        order.verify(store).release(claim, true);
        verify(gateway, never()).checkout(any(), any(), any(), any());
        verify(store, never()).checkoutReady(any(), any());
    }

    @Test
    void checkoutCreationFailureReleasesClaimWithoutPostingAnUrl() {
        var claim = claim(false);
        var snapshot = new BillingGateway.Snapshot("cus_test", "ref", List.of(), List.of());
        Instant expiry = NOW.plusSeconds(2100);
        var failure = BillingException.unavailable();
        when(store.claim(USER, false)).thenReturn(claim);
        when(gateway.snapshot("cus_test", "ref")).thenReturn(snapshot);
        when(store.reserveCheckout(eq(claim), anyString(), anyString()))
                .thenReturn(new BillingStore.CheckoutWork("operation", expiry, null));
        when(gateway.checkout("cus_test", "ref", "operation", expiry)).thenThrow(failure);

        assertThatThrownBy(() -> service.checkout(USER, KEY)).isSameAs(failure);

        verify(store).release(claim, false);
        verify(store, never()).checkoutReady(any(), any());
    }

    @Test
    void stopsUncertainCustomerCreationAtTwentyThreeHours() {
        var claim = new BillingStore.Claim(USER, "ref", null, "token", false, NOW.minusSeconds(23 * 3600));
        when(store.claim(USER, false)).thenReturn(claim);

        assertThatThrownBy(() -> service.checkout(USER, KEY))
                .isInstanceOfSatisfying(BillingException.class, error ->
                        assertThat(error.code()).isEqualTo("BILLING_REVIEW_REQUIRED"));

        verify(store).release(claim, false);
        verifyNoInteractions(gateway);
        verify(store, never()).bind(any(), any());
    }

    @Test
    void retriesInboxWriteOnceWhenConcurrentDeliveryWinsInsert() {
        byte[] payload = {1, 2};
        when(gateway.verify(payload, "synthetic-signature")).thenReturn(EVENT);
        doThrow(new DataIntegrityViolationException("duplicate")).doNothing().when(store).record(EVENT);

        service.webhook(payload, "synthetic-signature");

        var order = inOrder(gateway, store);
        order.verify(gateway).verify(payload, "synthetic-signature");
        order.verify(store, times(2)).record(EVENT);
        verifyNoMoreInteractions(gateway, store);
    }

    @Test
    void leavesEventRetryableWhenProviderCannotResolveCustomer() {
        var work = new BillingStore.EventWork(3L, "event-token", EVENT);
        var failure = BillingException.unavailable();
        when(gateway.eventCustomer(EVENT)).thenThrow(failure);

        assertThatThrownBy(() -> service.process(work)).isSameAs(failure);

        verify(store).finish(work, false);
        verify(store, never()).userForCustomer(any());
        verify(store, never()).claim(any(), anyBoolean());
    }

    @Test
    void finishesEventForUnknownCustomerWithoutSchedulingUserSync() {
        var work = new BillingStore.EventWork(3L, "event-token", EVENT);
        when(gateway.eventCustomer(EVENT)).thenReturn("cus_unknown");
        when(store.userForCustomer("cus_unknown")).thenReturn(null);

        service.process(work);

        verify(store).userForCustomer("cus_unknown");
        verify(store).finish(work, true);
        verify(store, never()).claim(any(), anyBoolean());
    }

    @Test
    void deletesLocalDataOnlyAfterRemoteDeletionSucceeds() {
        var claim = claim(true);
        when(store.claim(USER, true)).thenReturn(claim);

        service.synchronize(USER);

        var order = inOrder(store, gateway, deletion);
        order.verify(store).claim(USER, true);
        order.verify(gateway).deleteCustomer("cus_test");
        order.verify(deletion).delete(USER);
        order.verify(store).release(claim, true);
    }

    @Test
    void preservesLocalDataWhenRemoteDeletionFails() {
        var claim = claim(true);
        var failure = BillingException.unavailable();
        when(store.claim(USER, true)).thenReturn(claim);
        doThrow(failure).when(gateway).deleteCustomer("cus_test");

        assertThatThrownBy(() -> service.synchronize(USER)).isSameAs(failure);
        verify(store).release(claim, false);
        verifyNoInteractions(deletion);
    }

    private static BillingStore.Claim claim(boolean deleting) {
        return new BillingStore.Claim(USER, "ref", "cus_test", "token", deleting, NOW);
    }
}
