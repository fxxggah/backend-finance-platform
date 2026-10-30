package com.finance.creditcard.dto.response;

import com.finance.common.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Detalhe inclui transações e pagamentos
record InvoiceDetailResponse(
        String reference,
        LocalDate closingDate,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal paid,
        InvoiceStatus status,
        List<Object> transactions, // Substituir por TransactionResponse
        List<InvoicePaymentResponse> payments
) {}