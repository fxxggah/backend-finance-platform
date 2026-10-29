package com.finance.transaction.dto.request;

import com.finance.common.enums.TransactionType;

import java.time.LocalDate;

/**
 * Filtros da listagem de transações. Todos opcionais.
 */
public record TransactionFilter(
        LocalDate from,
        LocalDate to,
        TransactionType type,
        Long categoryId,
        Long accountId,
        Long creditCardId,
        String q) {
}