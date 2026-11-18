package com.finance.billing.service;

import com.finance.billing.repository.BillingInvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class ProAccessService {
    private final BillingInvoiceRepository invoices;
    public ProAccessService(BillingInvoiceRepository invoices) { this.invoices = invoices; }
    @Transactional(readOnly=true)
    public Instant paidUntil(Long userId, Instant now) { return invoices.paidUntil(userId, now); }
    public static Instant max(Instant manual, Instant paid) { return manual == null ? paid : paid == null || manual.isAfter(paid) ? manual : paid; }
}
