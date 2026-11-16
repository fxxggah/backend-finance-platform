package com.finance.ai.model;

import com.finance.common.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExtractedTransaction(TransactionType type, BigDecimal amount, LocalDate date,
        Integer installments, String categoryRef, String accountRef, String creditCardRef) {
    @Override public String toString() { return "ExtractedTransaction[conteúdo omitido]"; }
}
