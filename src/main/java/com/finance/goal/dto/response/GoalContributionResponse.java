package com.finance.goal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalContributionResponse(
        Long id,
        BigDecimal amount,
        LocalDate date,
        String notes
) {}