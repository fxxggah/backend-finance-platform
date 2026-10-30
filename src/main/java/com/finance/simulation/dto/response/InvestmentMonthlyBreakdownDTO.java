package com.finance.simulation.dto.response;

import java.math.BigDecimal;

public record InvestmentMonthlyBreakdownDTO(
        int month,
        BigDecimal currentInvested,
        BigDecimal interestEarned,
        BigDecimal totalBalance
) {}