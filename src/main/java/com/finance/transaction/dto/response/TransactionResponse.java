package com.finance.transaction.dto.response;

import com.finance.common.enums.TransactionType;
import com.finance.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record TransactionResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        LocalDate date,
        String description,
        String notes,
        CategoryInfo category,
        AccountInfo account,
        CreditCardInfo creditCard,
        String invoiceReference,
        InstallmentInfo installment) {

    public record CategoryInfo(Long id, String name, String color, String icon) {
    }

    public record AccountInfo(Long id, String name) {
    }

    public record CreditCardInfo(Long id, String name) {
    }

    public record InstallmentInfo(String groupId, Integer number, Integer total) {
    }

    /**
     * Deve ser chamado dentro de uma transação (as associações são LAZY).
     */
    public static TransactionResponse from(Transaction t) {
        CategoryInfo category = new CategoryInfo(
                t.getCategory().getId(),
                t.getCategory().getName(),
                t.getCategory().getColor(),
                t.getCategory().getIcon());

        AccountInfo account = t.getAccount() == null
                ? null
                : new AccountInfo(t.getAccount().getId(), t.getAccount().getName());

        CreditCardInfo creditCard = t.getCreditCard() == null
                ? null
                : new CreditCardInfo(t.getCreditCard().getId(), t.getCreditCard().getName());

        String invoiceReference = t.getInvoiceReference() == null
                ? null
                : YearMonth.from(t.getInvoiceReference()).toString(); // yyyy-MM

        InstallmentInfo installment = t.getInstallmentGroupId() == null
                ? null
                : new InstallmentInfo(t.getInstallmentGroupId(), t.getInstallmentNumber(), t.getInstallmentTotal());

        return new TransactionResponse(
                t.getId(),
                t.getType(),
                t.getAmount(),
                t.getDate(),
                t.getDescription(),
                t.getNotes(),
                category,
                account,
                creditCard,
                invoiceReference,
                installment);
    }
}