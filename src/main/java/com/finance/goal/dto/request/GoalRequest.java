package com.finance.goal.dto.request;

import com.finance.common.enums.GoalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalRequest(
        @NotNull(message = "O tipo da meta é obrigatório")
        GoalType type,

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name,

        @Size(max = 255)
        String description,

        @Size(max = 40)
        String icon,

        @Size(max = 7)
        String color,

        @NotNull(message = "O valor alvo é obrigatório")
        @Positive(message = "O valor alvo deve ser maior que zero")
        BigDecimal targetAmount,

        Long categoryId,
        LocalDate startDate,
        LocalDate endDate
) {}