package com.finance.user.mapper;

import com.finance.user.dto.response.UserResponse;
import com.finance.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    @Test
    @DisplayName("toResponse: deve mapear todos os campos do usuário")
    void toResponse_shouldMapAllFields() {
        User user = new User("google-123", "joao@example.com", "João Silva", "https://img.example.com/joao.png");
        user.setId(1L);

        UserResponse response = UserMapper.toResponse(user);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("João Silva");
        assertThat(response.email()).isEqualTo("joao@example.com");
        assertThat(response.pictureUrl()).isEqualTo("https://img.example.com/joao.png");
    }

    @Test
    @DisplayName("toResponse: deve manter pictureUrl nulo quando o usuário não tiver foto")
    void toResponse_shouldKeepPictureUrlNull_whenUserHasNoPicture() {
        User user = new User("google-123", "joao@example.com", "João Silva", null);
        user.setId(2L);

        UserResponse response = UserMapper.toResponse(user);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.pictureUrl()).isNull();
    }

    @Test
    @DisplayName("toResponse: deve retornar null quando o usuário for null")
    void toResponse_shouldReturnNull_whenUserIsNull() {
        assertThat(UserMapper.toResponse(null)).isNull();
    }
}