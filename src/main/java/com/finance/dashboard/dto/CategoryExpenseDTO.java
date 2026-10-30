package com.finance.dashboard.dto;

import java.math.BigDecimal;

public record CategoryExpenseDTO(
        Long categoryId,
        String name,
        String color,
        String icon,
        BigDecimal total,
        BigDecimal percentage,
        Long count
) {
    public CategoryExpenseDTO(Long categoryId, String name, String color, String icon, BigDecimal total, Double percentage, Long count) {
        this(categoryId, name, color, icon, total, BigDecimal.valueOf(percentage), count);
    }
}