package com.finance.simulation.dto.response;

import java.math.BigDecimal;

public record AmortizationScheduleDTO(
        int month,
        BigDecimal paymentAmount,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal remainingBalance
) {}