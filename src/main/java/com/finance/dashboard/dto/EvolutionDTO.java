package com.finance.dashboard.dto;

import com.finance.common.enums.Granularity;

import java.util.List;

public record EvolutionDTO(
        Granularity granularity,
        List<EvolutionPointDTO> points
) {}