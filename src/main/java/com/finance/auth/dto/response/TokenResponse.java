package com.finance.auth.dto.response;

public record TokenResponse(
                String accessToken,
                String refreshToken,
                long expiresIn) {
}
