package com.finance.user.mapper;

import com.finance.user.dto.response.UserResponse;
import com.finance.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    @Test
    @DisplayName("Deve mapear User para UserResponse corretamente")
    void shouldMapUserToUserResponse() {
        User user = new User("google-123", "test@test.com", "Test User", "https://avatar.png");
        user.setId(10L);

        UserResponse response = UserMapper.toResponse(user);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Test User");
        assertThat(response.email()).isEqualTo("test@test.com");
        assertThat(response.pictureUrl()).isEqualTo("https://avatar.png");
    }

    @Test
    @DisplayName("Deve retornar null quando user for null")
    void shouldReturnNullWhenUserIsNull() {
        UserResponse response = UserMapper.toResponse(null);

        assertThat(response).isNull();
    }
}
