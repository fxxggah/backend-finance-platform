package com.finance.account.dto.response;

import com.finance.account.entity.Account;
import com.finance.common.enums.AccountType;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        BigDecimal balance,
        String color,
        String icon,
        boolean archived
) {
    public static AccountResponse from(Account account, BigDecimal balance) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                balance,
                account.getColor(),
                account.getIcon(),
                account.isArchived());
    }
}