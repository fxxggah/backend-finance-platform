package com.finance.auth.dto.response;

import com.finance.user.dto.response.UserResponse;

public record AuthResponse(
                String accessToken,
                String refreshToken,
                long expiresIn,
                UserResponse user) {
}
