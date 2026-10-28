package com.finance.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
                @NotBlank(message = "O ID Token é obrigatório") String idToken) {
}
