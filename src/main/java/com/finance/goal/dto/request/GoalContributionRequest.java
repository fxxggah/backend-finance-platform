package com.finance.goal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalContributionRequest(
        @NotNull(message = "O valor da contribuição é obrigatório")
        BigDecimal amount,

        @NotNull(message = "A data da contribuição é obrigatória")
        LocalDate date,

        @Size(max = 255)
        String notes
) {}