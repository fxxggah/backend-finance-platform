package com.finance.user.dto.response;

public record UserResponse(
        Long id,
        String name,
        String email,
        String pictureUrl,
        String plan,
        java.time.Instant proExpiresAt) {
    public UserResponse(Long id, String name, String email, String pictureUrl) {
        this(id, name, email, pictureUrl, "BASIC", null);
    }
}
