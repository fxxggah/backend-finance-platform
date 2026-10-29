package com.finance.transfer.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferResponse(
        Long id,
        AccountSummary fromAccount,
        AccountSummary toAccount,
        BigDecimal amount,
        LocalDate date,
        String description
) {
    public record AccountSummary(Long id, String name, String color, String icon) {}
}