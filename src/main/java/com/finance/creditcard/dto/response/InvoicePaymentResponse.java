package com.finance.creditcard.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoicePaymentResponse(
        Long id,
        BigDecimal amount,
        LocalDate date,
        Long accountId) {}