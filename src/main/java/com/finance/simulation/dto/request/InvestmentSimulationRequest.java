package com.finance.simulation.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record InvestmentSimulationRequest(
        @NotNull
        @DecimalMin("0.0")
        BigDecimal initialAmount,

        @NotNull
        @DecimalMin("0.0")
        BigDecimal monthlyContribution,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        BigDecimal annualRate,

        @NotNull
        @Min(1)
        @Max(50)
        Integer years
) {}