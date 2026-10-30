package com.finance.dashboard.dto;

import com.finance.common.enums.Period;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardSummaryDTO(
        Period period,
        LocalDate from,
        LocalDate to,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        PreviousSummaryDTO previous
) {}