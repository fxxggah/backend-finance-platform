package com.finance.ai.dto;

import com.finance.common.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransactionDraftResponse(TransactionType type, BigDecimal amount, LocalDate date,
        Long categoryId, Long accountId, Long creditCardId, Integer installments,
        List<String> missingFields, boolean requiresConfirmation,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Descrição curta sugerida a partir da fala", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED, maxLength = 255)
        String description) {
    public TransactionDraftResponse { missingFields = List.copyOf(missingFields); }
    @Override public String toString() { return "TransactionDraftResponse[conteúdo omitido]"; }
}
