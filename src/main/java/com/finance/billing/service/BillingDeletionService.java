package com.finance.billing.service;

import com.finance.billing.exception.BillingException;
import com.finance.user.service.UserDataDeletionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class BillingDeletionService {
    private final BillingStore store;
    private final ObjectProvider<BillingService> billing;
    private final UserDataDeletionService deletion;
    public BillingDeletionService(BillingStore store, ObjectProvider<BillingService> billing, UserDataDeletionService deletion) {
        this.store = store; this.billing = billing; this.deletion = deletion;
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void delete(Long userId) {
        if (!store.beginDeletion(userId)) { deletion.delete(userId); return; }
        var service = billing.getIfAvailable();
        if (service == null) throw BillingException.unavailable();
        service.synchronize(userId);
    }
}
