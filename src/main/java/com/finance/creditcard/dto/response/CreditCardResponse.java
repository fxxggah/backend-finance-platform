package com.finance.creditcard.dto.response;

import java.math.BigDecimal;

public record CreditCardResponse(
        Long id,
        String name,
        BigDecimal creditLimit,
        BigDecimal usedLimit,
        BigDecimal availableLimit,
        Integer closingDay,
        Integer dueDay,
        String color,
        String icon,
        boolean archived,
        InvoiceResponse currentInvoice
) {}