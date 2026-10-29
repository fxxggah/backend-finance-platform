package com.finance.transaction.dto.request;

import com.finance.common.enums.TransactionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload de criação e edição de transação.
 * <p>
 * Informe {@code accountId} OU {@code creditCardId} (nunca os dois).
 * {@code amount} e {@code installments} são ignorados na edição de compra parcelada.
 */
public record TransactionRequest(

        @NotNull(message = "é obrigatório")
        TransactionType type,

        @DecimalMin(value = "0.01", message = "deve ser maior que zero")
        @DecimalMax(value = "999999999.99", message = "valor acima do máximo permitido")
        @Digits(integer = 9, fraction = 2, message = "no máximo 2 casas decimais")
        BigDecimal amount,

        @NotNull(message = "é obrigatório")
        Long categoryId,

        @NotNull(message = "é obrigatória")
        LocalDate date,

        @Size(max = 255, message = "no máximo 255 caracteres")
        String description,

        @Size(max = 500, message = "no máximo 500 caracteres")
        String notes,

        Long accountId,

        Long creditCardId,

        @Min(value = 1, message = "mínimo 1 parcela")
        @Max(value = 24, message = "máximo 24 parcelas")
        Integer installments) {
}