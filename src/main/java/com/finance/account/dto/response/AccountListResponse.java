package com.finance.account.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AccountListResponse(
        List<AccountResponse> accounts,
        BigDecimal totalBalance,
        BigDecimal creditCardDebt
) {
}