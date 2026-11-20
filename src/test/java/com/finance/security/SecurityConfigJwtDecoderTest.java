package com.finance.security;

import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigJwtDecoderTest {
    private static final String SIGNING_FIXTURE = "synthetic-signing-material-for-decoder-tests";
    private static final long USER_ID = 42L;

    @Mock private ObjectProvider<UserRepository> userRepositories;
    @Mock private UserRepository users;

    private SecurityConfig config;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "jwtSecret", SIGNING_FIXTURE);
        ReflectionTestUtils.setField(config, "userRepositories", userRepositories);
        decoder = config.jwtDecoder();
    }

    @Test
    void acceptsTokenEmittedByJwtServiceForActiveUser() {
        when(userRepositories.getIfAvailable()).thenReturn(users);
        when(users.existsByIdAndActiveTrue(USER_ID)).thenReturn(true);
        String token = new JwtService(SIGNING_FIXTURE, 900).generateAccessToken(USER_ID);

        assertThat(decoder.decode(token).getSubject()).isEqualTo("42");
        verify(users).existsByIdAndActiveTrue(USER_ID);
    }

    @Test
    void rejectsSignatureFromADifferentKeyBeforeLookingUpUser() {
        String token = new JwtService("different-synthetic-signing-material-for-tests", 900)
                .generateAccessToken(USER_ID);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        verifyNoInteractions(userRepositories, users);
    }

    @Test
    void rejectsExpiredTokenBeyondAllowedClockSkew() {
        when(userRepositories.getIfAvailable()).thenReturn(users);
        when(users.existsByIdAndActiveTrue(USER_ID)).thenReturn(true);
        String token = signedToken("42", Instant.now().minusSeconds(300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"google-user", "0", "-1", "9223372036854775808"})
    void rejectsMissingOrInvalidInternalSubject(String subject) {
        String token = signedToken(subject, Instant.now().plusSeconds(900));

        assertRejectedUser(token);
        verifyNoInteractions(users);
    }

    @Test
    void rejectsPreviouslyIssuedTokenAfterUserIsDeactivatedOrRemoved() {
        when(userRepositories.getIfAvailable()).thenReturn(users);
        when(users.existsByIdAndActiveTrue(USER_ID)).thenReturn(true, false);
        String token = signedToken("42", Instant.now().plusSeconds(900));

        assertThat(decoder.decode(token).getSubject()).isEqualTo("42");
        assertRejectedUser(token);
    }

    @Test
    void rejectsTokenWhenUserLookupFailsWithoutExposingDatabaseError() {
        when(userRepositories.getIfAvailable()).thenReturn(users);
        when(users.existsByIdAndActiveTrue(USER_ID))
                .thenThrow(new DataAccessResourceFailureException("synthetic database failure"));
        String token = signedToken("42", Instant.now().plusSeconds(900));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageNotContaining("synthetic database failure");
        verify(users).existsByIdAndActiveTrue(USER_ID);
    }

    @Test
    void rejectsTokenWhenRepositoryIsUnavailable() {
        assertRejectedUser(signedToken("42", Instant.now().plusSeconds(900)));
        verify(userRepositories).getIfAvailable();
    }

    private String signedToken(String subject, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuedAt(expiresAt.minusSeconds(900)).expiresAt(expiresAt);
        if (subject != null) claims.subject(subject);
        return config.jwtEncoder().encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private void assertRejectedUser(String token) {
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOfSatisfying(JwtValidationException.class, error ->
                        assertThat(error.getErrors()).anySatisfy(detail -> {
                            assertThat(detail.getErrorCode()).isEqualTo("invalid_token");
                            assertThat(detail.getDescription()).isEqualTo("Usuário não disponível");
                        }));
    }
}
