package com.finance.simulation.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DebtPayoffSimulationRequest(
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal debtAmount,

        @NotNull
        @DecimalMin("0.0")
        BigDecimal monthlyRate,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal monthlyPayment
) {}