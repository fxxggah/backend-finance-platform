package com.finance.simulation.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record InvestmentSimulationResponse(
        BigDecimal totalInvested,
        BigDecimal totalInterest,
        BigDecimal finalAmount,
        List<InvestmentMonthlyBreakdownDTO> monthlyBreakdown,
        String disclaimer
) {
    public InvestmentSimulationResponse(BigDecimal totalInvested, BigDecimal totalInterest, BigDecimal finalAmount, List<InvestmentMonthlyBreakdownDTO> monthlyBreakdown) {
        this(totalInvested, totalInterest, finalAmount, monthlyBreakdown, "Simulação ilustrativa, não é recomendação financeira.");
    }
}