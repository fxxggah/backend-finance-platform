package com.finance.creditcard.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreditCardRequest(
        @NotBlank(message = "O nome é obrigatório") String name,
        @NotNull(message = "O limite é obrigatório") @Positive(message = "O limite deve ser maior que zero") BigDecimal creditLimit,
        @NotNull(message = "O dia de fechamento é obrigatório") @Min(1) @Max(28) Integer closingDay,
        @NotNull(message = "O dia de vencimento é obrigatório") @Min(1) @Max(28) Integer dueDay,
        String color,
        String icon
) {}