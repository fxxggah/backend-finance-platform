package com.finance.dashboard.dto;

import java.math.BigDecimal;

public record TransactionDashboardDTO(
        Long id,
        BigDecimal amount,
        String description,
        String creditCardName
) {}