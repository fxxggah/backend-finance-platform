package com.finance.dashboard.dto;

import com.finance.common.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRecentDTO(
        Long id,
        BigDecimal amount,
        LocalDate date,
        TransactionType type,
        String categoryName,
        String accountName,
        String creditCardName
) {}