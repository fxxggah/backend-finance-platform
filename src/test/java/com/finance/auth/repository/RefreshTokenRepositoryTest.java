package com.finance.auth.repository;

import com.finance.auth.entity.RefreshToken;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(new User("google-owner", "owner@example.com", "Owner", null));
        other = userRepository.saveAndFlush(new User("google-other", "other@example.com", "Other", null));
    }

    private RefreshToken persist(User user, String tokenHash, boolean revoked) {
        RefreshToken token = new RefreshToken(user, tokenHash, Instant.now().plus(7, ChronoUnit.DAYS));
        token.setRevoked(revoked);
        return refreshTokenRepository.saveAndFlush(token);
    }

    private boolean isRevoked(RefreshToken token) {
        return refreshTokenRepository.findById(token.getId()).orElseThrow().isRevoked();
    }

    // ---------- persistência ----------

    @Test
    @DisplayName("save: deve persistir o token com id, auditoria, revoked=false e vínculo com o usuário")
    void save_shouldPersistTokenWithDefaults() {
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        RefreshToken saved = refreshTokenRepository.saveAndFlush(new RefreshToken(owner, "hash-1", expiresAt));
        entityManager.clear();

        RefreshToken reloaded = refreshTokenRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getTokenHash()).isEqualTo("hash-1");
        assertThat(reloaded.isRevoked()).isFalse();
        assertThat(reloaded.getExpiresAt()).isCloseTo(expiresAt, within(1, ChronoUnit.SECONDS));
        assertThat(reloaded.getUser().getId()).isEqualTo(owner.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save: deve falhar ao inserir tokenHash duplicado")
    void save_shouldFail_whenTokenHashIsDuplicated() {
        persist(owner, "dup-hash", false);
        RefreshToken duplicated = new RefreshToken(other, "dup-hash", Instant.now().plus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(duplicated))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save: deve falhar quando o usuário (obrigatório) for nulo")
    void save_shouldFail_whenUserIsNull() {
        RefreshToken invalid = new RefreshToken(null, "orphan-hash", Instant.now().plus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(invalid))
                .isInstanceOf(Exception.class);
    }

    // ---------- findByTokenHash ----------

    @Test
    @DisplayName("findByTokenHash: deve retornar o token quando o hash existir")
    void findByTokenHash_shouldReturnToken_whenExists() {
        persist(owner, "hash-1", false);

        Optional<RefreshToken> result = refreshTokenRepository.findByTokenHash("hash-1");

        assertThat(result).isPresent();
        assertThat(result.get().getUser().getId()).isEqualTo(owner.getId());
        assertThat(result.get().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("findByTokenHash: deve retornar também tokens revogados (a detecção de reuso depende disso)")
    void findByTokenHash_shouldReturnRevokedToken() {
        persist(owner, "revoked-hash", true);

        Optional<RefreshToken> result = refreshTokenRepository.findByTokenHash("revoked-hash");

        assertThat(result).isPresent();
        assertThat(result.get().isRevoked()).isTrue();
    }

    @Test
    @DisplayName("findByTokenHash: deve retornar vazio quando o hash não existir")
    void findByTokenHash_shouldReturnEmpty_whenNotExists() {
        persist(owner, "hash-1", false);

        assertThat(refreshTokenRepository.findByTokenHash("inexistente")).isEmpty();
    }

    // ---------- findAllByUserId ----------

    @Test
    @DisplayName("findAllByUserId: deve retornar apenas os tokens do usuário (revogados inclusive)")
    void findAllByUserId_shouldReturnOnlyUserTokens() {
        persist(owner, "owner-1", false);
        persist(owner, "owner-2", true);
        persist(other, "other-1", false);

        List<RefreshToken> result = refreshTokenRepository.findAllByUserId(owner.getId());

        assertThat(result).extracting(RefreshToken::getTokenHash)
                .containsExactlyInAnyOrder("owner-1", "owner-2");
    }

    @Test
    @DisplayName("findAllByUserId: deve retornar lista vazia quando o usuário não tem tokens")
    void findAllByUserId_shouldReturnEmpty_whenUserHasNoTokens() {
        persist(other, "other-1", false);

        assertThat(refreshTokenRepository.findAllByUserId(owner.getId())).isEmpty();
    }

    // ---------- revokeAllByUserId ----------

    @Test
    @DisplayName("revokeAllByUserId: deve revogar todos os tokens do usuário e preservar os de outros")
    void revokeAllByUserId_shouldRevokeOnlyUserTokens() {
        RefreshToken ownerActive = persist(owner, "owner-1", false);
        RefreshToken ownerAlreadyRevoked = persist(owner, "owner-2", true);
        RefreshToken otherToken = persist(other, "other-1", false);

        refreshTokenRepository.revokeAllByUserId(owner.getId());
        entityManager.flush();
        entityManager.clear(); // a query @Modifying não atualiza o contexto de persistência

        assertThat(isRevoked(ownerActive)).isTrue();
        assertThat(isRevoked(ownerAlreadyRevoked)).isTrue();
        assertThat(isRevoked(otherToken)).isFalse();
    }

    @Test
    @DisplayName("revokeAllByUserId: não deve falhar nem afetar outros usuários quando não há tokens")
    void revokeAllByUserId_shouldDoNothing_whenUserHasNoTokens() {
        RefreshToken otherToken = persist(other, "other-1", false);

        refreshTokenRepository.revokeAllByUserId(owner.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(isRevoked(otherToken)).isFalse();
    }

    // ---------- deleteAllByUserId ----------

    @Test
    @DisplayName("deleteAllByUserId: deve apagar todos os tokens do usuário e preservar os de outros")
    void deleteAllByUserId_shouldDeleteOnlyUserTokens() {
        persist(owner, "owner-1", false);
        persist(owner, "owner-2", true);
        persist(other, "other-1", false);

        refreshTokenRepository.deleteAllByUserId(owner.getId());
        refreshTokenRepository.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.findAllByUserId(owner.getId())).isEmpty();
        assertThat(refreshTokenRepository.findAllByUserId(other.getId()))
                .extracting(RefreshToken::getTokenHash).containsExactly("other-1");
    }
}