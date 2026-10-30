package com.finance.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EvolutionPointDTO(
        String label,
        LocalDate from,
        LocalDate to,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance
) {}