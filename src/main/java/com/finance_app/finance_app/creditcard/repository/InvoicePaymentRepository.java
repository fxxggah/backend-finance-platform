package com.finance_app.finance_app.creditcard.repository;

import com.finance_app.finance_app.creditcard.entity.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {

    Optional<InvoicePayment> findByIdAndUserId(Long id, Long userId);

    List<InvoicePayment> findAllByUserIdAndCreditCardIdAndInvoiceReference(Long userId, Long creditCardId, LocalDate invoiceReference);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByAccountId(Long accountId);

    boolean existsByCreditCardId(Long creditCardId);
}

