package com.finance.creditcard.dto.response;


import com.finance.common.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceResponse(
        String reference, // ex: "2026-10"
        LocalDate closingDate,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal paid,
        InvoiceStatus status
) {}