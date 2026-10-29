package com.finance.transfer.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferRequest(
        @NotNull(message = "A conta de origem é obrigatória")
        Long fromAccountId,

        @NotNull(message = "A conta de destino é obrigatória")
        Long toAccountId,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 9, fraction = 2)
        BigDecimal amount,

        @NotNull(message = "A data é obrigatória")
        LocalDate date,

        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
        String description
) {}