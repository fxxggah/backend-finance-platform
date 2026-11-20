package com.finance.billing.service;

import com.finance.billing.exception.BillingException;
import com.finance.billing.provider.BillingGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class BillingWorkerTest {
    @Test
    void keepsProcessingEventsAndUsersAfterIndividualFailures() {
        var store = mock(BillingStore.class);
        var billing = mock(BillingService.class);
        var worker = new BillingWorker(store, billing);
        var first = new BillingStore.EventWork(11L, "first", new BillingGateway.Event("evt_first", "invoice.paid", "in_first"));
        var last = new BillingStore.EventWork(14L, "last", new BillingGateway.Event("evt_last", "invoice.paid", "in_last"));
        when(store.dueEvents()).thenReturn(List.of(11L, 12L, 13L, 14L));
        when(store.claimEvent(11L)).thenReturn(first);
        when(store.claimEvent(12L)).thenThrow(BillingException.busy());
        when(store.claimEvent(14L)).thenReturn(last);
        when(store.dueUsers()).thenReturn(List.of(21L, 22L));
        doThrow(BillingException.unavailable()).when(billing).process(first);
        doThrow(BillingException.unavailable()).when(billing).synchronize(21L);

        worker.process();

        var order = inOrder(store, billing);
        order.verify(store).dueEvents();
        order.verify(store).claimEvent(11L);
        order.verify(billing).process(first);
        order.verify(store).claimEvent(12L);
        order.verify(store).claimEvent(13L);
        order.verify(store).claimEvent(14L);
        order.verify(billing).process(last);
        order.verify(store).dueUsers();
        order.verify(billing).synchronize(21L);
        order.verify(billing).synchronize(22L);
        verifyNoMoreInteractions(billing, store);
    }
}
