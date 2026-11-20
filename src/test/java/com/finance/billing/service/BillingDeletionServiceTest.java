package com.finance.billing.service;

import com.finance.billing.exception.BillingException;
import com.finance.user.service.UserDataDeletionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingDeletionServiceTest {
    private static final long USER = 7L;

    @Mock private BillingStore store;
    @Mock private ObjectProvider<BillingService> billing;
    @Mock private UserDataDeletionService deletion;
    @Mock private BillingService billingService;
    @InjectMocks private BillingDeletionService service;

    @Test
    void deletesLocallyWhenThereIsNoBillingLink() {
        service.delete(USER);

        var order = inOrder(store, deletion);
        order.verify(store).beginDeletion(USER);
        order.verify(deletion).delete(USER);
        verifyNoInteractions(billing, billingService);
    }

    @Test
    void consultsBillingOnlyAfterMarkingAccountForDeletion() {
        when(store.beginDeletion(USER)).thenReturn(true);
        when(billing.getIfAvailable()).thenReturn(billingService);

        service.delete(USER);

        var order = inOrder(store, billing, billingService);
        order.verify(store).beginDeletion(USER);
        order.verify(billing).getIfAvailable();
        order.verify(billingService).synchronize(USER);
        verifyNoInteractions(deletion);
    }

    @Test
    void unavailableBillingLeavesAccountPendingWithoutPhysicalDeletion() {
        when(store.beginDeletion(USER)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(USER))
                .isInstanceOfSatisfying(BillingException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.code()).isEqualTo("BILLING_UNAVAILABLE");
                });
        verify(store).beginDeletion(USER);
        verify(billing).getIfAvailable();
        verifyNoInteractions(deletion, billingService);
    }

    @Test
    void propagatesFailedSynchronizationWithoutDeletingLocally() {
        when(store.beginDeletion(USER)).thenReturn(true);
        when(billing.getIfAvailable()).thenReturn(billingService);
        var failure = BillingException.unavailable();
        doThrow(failure).when(billingService).synchronize(USER);

        assertThatThrownBy(() -> service.delete(USER)).isSameAs(failure);
        verifyNoInteractions(deletion);
    }
}
