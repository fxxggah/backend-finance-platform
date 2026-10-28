package com.finance.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
                @NotBlank(message = "O refresh token é obrigatório") String refreshToken) {
}
