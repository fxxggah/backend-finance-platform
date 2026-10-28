package com.finance_app.finance_app.user.dto;

public record UserResponse(
    Long id,
    String name,
    String email,
    String pictureUrl
) {}

