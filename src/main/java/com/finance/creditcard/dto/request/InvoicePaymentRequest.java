package com.finance.creditcard.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoicePaymentRequest(
        @NotNull(message = "A conta é obrigatória") Long accountId,
        @NotNull(message = "O valor é obrigatório") @Positive BigDecimal amount,
        @NotNull(message = "A data é obrigatória") LocalDate date
) {}