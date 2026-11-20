package com.finance.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void issuesHs256TokenWithInternalUserIdAndConfiguredLifetime() {
        var service = new JwtService("synthetic-signing-material-for-jwt-tests", 780);
        Instant before = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        var token = service.decode(service.generateAccessToken(42L));

        assertThat(token.getHeaders()).containsEntry("alg", "HS256");
        assertThat(token.getSubject()).isEqualTo("42");
        assertThat(service.extractUserId(token)).isEqualTo(42L);
        assertThat(token.getIssuedAt()).isBetween(before, Instant.now());
        assertThat(token.getExpiresAt()).isEqualTo(token.getIssuedAt().plusSeconds(780));
        assertThat(service.getAccessTokenExpiration()).isEqualTo(780);
        assertThat(token.getClaims()).containsOnlyKeys("sub", "iat", "exp");
    }
}
