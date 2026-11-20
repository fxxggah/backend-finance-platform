package com.finance.user.mapper;

import com.finance.user.dto.response.UserResponse;
import com.finance.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    @Test
    void exposesEffectivePaidOrManualValidityAndRequiresAnActiveUser() {
        Instant now = Instant.parse("2026-11-20T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        Instant first = now.plusSeconds(60);
        Instant last = now.plusSeconds(120);
        User user = new User("synthetic", "synthetic@example.test", "Synthetic", null);

        assertPlan(UserMapper.toResponse(user, clock, now.minusSeconds(1)), "BASIC", now.minusSeconds(1));
        assertPlan(UserMapper.toResponse(user, clock, now), "BASIC", now);
        assertPlan(UserMapper.toResponse(user, clock, first), "PRO", first);

        user.setProExpiresAt(first);
        assertPlan(UserMapper.toResponse(user, clock, last), "PRO", last);
        user.setProExpiresAt(last);
        assertPlan(UserMapper.toResponse(user, clock, first), "PRO", last);

        user.setActive(false);
        assertPlan(UserMapper.toResponse(user, clock, first), "BASIC", last);
    }

    private static void assertPlan(UserResponse response, String plan, Instant expiration) {
        assertThat(response.plan()).isEqualTo(plan);
        assertThat(response.proExpiresAt()).isEqualTo(expiration);
    }

    @Test
    void proExpiresExactlyAtClockInstantAndInactiveUserIsNeverPro() {
        var instant = java.time.Instant.parse("2026-11-16T12:00:00Z");
        var clock = java.time.Clock.fixed(instant, java.time.ZoneOffset.UTC);
        User user = new User("synthetic", "synthetic@example.com", "Synthetic", null);
        assertThat(UserMapper.toResponse(user, clock).plan()).isEqualTo("BASIC");
        user.setProExpiresAt(instant.plusSeconds(1));
        assertThat(UserMapper.toResponse(user, clock).plan()).isEqualTo("PRO");
        user.setProExpiresAt(instant);
        assertThat(UserMapper.toResponse(user, clock).plan()).isEqualTo("BASIC");
        user.setProExpiresAt(instant.plusSeconds(1)); user.setActive(false);
        assertThat(UserMapper.toResponse(user, clock).plan()).isEqualTo("BASIC");
    }

    @Test
    @DisplayName("toResponse: deve mapear todos os campos do usuário")
    void toResponse_shouldMapAllFields() {
        User user = new User("google-123", "joao@example.com", "João Silva", "https://img.example.com/joao.png");
        user.setId(1L);

        UserResponse response = UserMapper.toResponse(user, java.time.Clock.systemUTC());

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

        UserResponse response = UserMapper.toResponse(user, java.time.Clock.systemUTC());

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.pictureUrl()).isNull();
    }

    @Test
    @DisplayName("toResponse: deve retornar null quando o usuário for null")
    void toResponse_shouldReturnNull_whenUserIsNull() {
        assertThat(UserMapper.toResponse(null, java.time.Clock.systemUTC())).isNull();
    }
}
