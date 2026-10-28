package com.finance_app.finance_app.user.mapper;

import com.finance_app.finance_app.user.dto.UserResponse;
import com.finance_app.finance_app.user.entity.User;

public final class UserMapper {

    private UserMapper() {}

    public static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getPictureUrl()
        );
    }
}

