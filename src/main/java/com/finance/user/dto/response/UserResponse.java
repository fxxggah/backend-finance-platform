package com.finance.user.dto.response;

public record UserResponse(
        Long id,
        String name,
        String email,
        String pictureUrl) {
}
