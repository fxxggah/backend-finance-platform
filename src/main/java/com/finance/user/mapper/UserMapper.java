package com.finance.user.mapper;

import com.finance.user.dto.response.UserResponse;
import com.finance.user.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user, java.time.Clock clock) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPictureUrl(),
                user.isActive() && user.getProExpiresAt() != null && user.getProExpiresAt().isAfter(clock.instant()) ? "PRO" : "BASIC",
                user.getProExpiresAt());
    }
}
