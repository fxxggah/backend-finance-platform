package com.finance.dashboard.dto;

import java.time.LocalDate;
import java.math.BigDecimal;

public record PreviousSummaryDTO(
        LocalDate from,
        LocalDate to,
        BigDecimal incomeChangePct,
        BigDecimal expenseChangePct
) {}