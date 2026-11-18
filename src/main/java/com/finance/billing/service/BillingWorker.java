package com.finance.billing.service;

import org.springframework.scheduling.annotation.Scheduled;

public class BillingWorker {
    private final BillingStore store;
    private final BillingService billing;
    public BillingWorker(BillingStore store, BillingService billing) { this.store = store; this.billing = billing; }
    @Scheduled(fixedDelayString="${app.billing.worker-delay-ms:5000}")
    public void process() {
        for (Long id : store.dueEvents()) {
            try { var work = store.claimEvent(id); if (work != null) billing.process(work); }
            catch (RuntimeException ignored) { /* Inbox conserva a tentativa e agenda recuperação sem logar payload. */ }
        }
        for (Long userId : store.dueUsers()) {
            try { billing.synchronize(userId); }
            catch (RuntimeException ignored) { /* Lease/fila persistentes limitam a próxima tentativa. */ }
        }
    }
}
