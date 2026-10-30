package com.finance.simulation.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DebtPayoffSimulationResponse(
        int months,
        BigDecimal totalPaid,
        BigDecimal totalInterest,
        List<AmortizationScheduleDTO> schedule,
        String disclaimer
) {
    public DebtPayoffSimulationResponse(int months, BigDecimal totalPaid, BigDecimal totalInterest, List<AmortizationScheduleDTO> schedule) {
        this(months, totalPaid, totalInterest, schedule, "Simulação ilustrativa, não é recomendação financeira.");
    }
}