package com.finance.creditcard.repository;

import com.finance.creditcard.entity.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {

    Optional<InvoicePayment> findByIdAndUserId(Long id, Long userId);

    List<InvoicePayment> findAllByUserIdAndCreditCardIdAndInvoiceReference(Long userId, Long creditCardId,
            LocalDate invoiceReference);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByAccountId(Long accountId);

    boolean existsByCreditCardId(Long creditCardId);

    @Query("SELECT COALESCE(SUM(ip.amount), 0) FROM InvoicePayment ip WHERE ip.creditCard.id = :creditCardId")
    BigDecimal sumTotalPaymentsByCreditCardId(@Param("creditCardId") Long creditCardId);

    @Query("SELECT COALESCE(SUM(ip.amount), 0) FROM InvoicePayment ip WHERE ip.creditCard.id = :creditCardId AND ip.invoiceReference = :invoiceReference")
    BigDecimal sumPaymentsByCreditCardIdAndInvoiceReference(@Param("creditCardId") Long creditCardId, @Param("invoiceReference") LocalDate invoiceReference);

    @Query("SELECT COALESCE(SUM(ip.amount), 0) FROM InvoicePayment ip WHERE ip.account.id = :accountId")
    BigDecimal sumPaymentsByAccountId(@Param("accountId") Long accountId);

    void deleteAllByUserId(Long userId);
}
