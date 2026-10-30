package com.finance.goal.dto.response;

import com.finance.common.enums.GoalStatus;
import com.finance.common.enums.GoalState;
import com.finance.common.enums.GoalType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalResponse(
        Long id,
        GoalType type,
        String name,
        String description,
        String icon,
        String color,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        LocalDate startDate,
        LocalDate endDate,
        GoalStatus status,
        GoalState state,
        BigDecimal percentage,
        BigDecimal monthlyNeeded,
        CategorySimpleDto category // Assumindo um DTO simples para a categoria
) {}