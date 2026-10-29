package com.finance.category.dto.request;

import com.finance.common.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 60, message = "nome deve ter no máximo 60 caracteres")
        String name,

        @NotNull(message = "tipo é obrigatório")
        TransactionType type,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "cor deve ser um hex válido (ex.: #3B82F6)")
        String color,

        @Pattern(regexp = "^[a-z0-9-]{1,40}$", message = "ícone inválido")
        String icon
) {
}