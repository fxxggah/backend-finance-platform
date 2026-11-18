package com.finance.user.mapper;

import com.finance.user.dto.response.UserResponse;
import com.finance.user.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user, java.time.Clock clock) {
        return toResponse(user, clock, null);
    }

    public static UserResponse toResponse(User user, java.time.Clock clock, java.time.Instant paidUntil) {
        if (user == null) {
            return null;
        }
        var expiresAt = com.finance.billing.service.ProAccessService.max(user.getProExpiresAt(), paidUntil);
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPictureUrl(),
                user.isActive() && expiresAt != null && expiresAt.isAfter(clock.instant()) ? "PRO" : "BASIC",
                expiresAt);
    }
}
