package com.finance.auth;

import com.finance.auth.dto.response.TokenResponse;
import com.finance.auth.entity.RefreshToken;
import com.finance.auth.repository.RefreshTokenRepository;
import com.finance.auth.service.AuthService;
import com.finance.security.JwtService;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.name=auth-refresh-test", "app.ai.enabled=false", "app.billing.enabled=false",
        "app.jwt.secret=auth-refresh-synthetic-test-key-at-least-32-bytes",
        "app.jwt.access-token-expiration=900", "app.jwt.refresh-token-expiration=604800",
        "app.google.client-id=synthetic-client", "app.cors.origins=http://localhost:3000",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false", "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthRefreshIntegrationTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @MockitoSpyBean JwtService jwt;
    @Autowired RefreshTokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactionManager;
    private User owner;
    private User other;

    @BeforeEach
    void setup() {
        owner = user();
        other = user();
    }

    @Test
    void reuseCommitsRevocationBeforeUnauthorizedAndPreservesOtherUsersTokens() throws Exception {
        String raw = UUID.randomUUID().toString();
        token(owner, raw, true);
        RefreshToken sibling = token(owner, UUID.randomUUID().toString(), false);
        RefreshToken independent = token(other, UUID.randomUUID().toString(), false);

        refresh(raw).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));

        assertThat(tokens.findById(sibling.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(tokens.findAllByUserId(owner.getId())).allMatch(RefreshToken::isRevoked);
        assertThat(tokens.findById(independent.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void concurrentRefreshRotatesOnceAndReuseRevokesTheCommittedReplacement() throws Exception {
        String raw = UUID.randomUUID().toString();
        token(owner, raw, false);
        token(owner, UUID.randomUUID().toString(), false);
        RefreshToken independent = token(other, UUID.randomUUID().toString(), false);
        var rotated = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);

        // Keep the first real transaction uncommitted while the second request starts.
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var first = pool.submit(() -> new TransactionTemplate(transactionManager).execute(tx -> {
                    TokenResponse response = auth.refresh(raw);
                    rotated.countDown();
                    await(release);
                    return response;
                }));
                assertThat(rotated.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> {
                    secondStarted.countDown();
                    return refresh(raw).andReturn();
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    second.get(2, TimeUnit.SECONDS);
                } catch (TimeoutException expected) {
                    // A serialized request waits until the first transaction commits.
                } finally {
                    release.countDown();
                }
                TokenResponse replacement = first.get(10, TimeUnit.SECONDS);
                MvcResult rejected = second.get(10, TimeUnit.SECONDS);
                assertThat(rejected.getResponse().getStatus()).isEqualTo(401);
                assertThat(tokens.findAllByUserId(owner.getId())).hasSize(3).allMatch(RefreshToken::isRevoked);
                assertThat(tokens.findById(independent.getId()).orElseThrow().isRevoked()).isFalse();
                refresh(replacement.refreshToken()).andExpect(status().isUnauthorized());
            } finally {
                release.countDown();
                pool.shutdownNow();
            }
        }
    }

    @Test
    void unexpectedFailureRollsBackBothRevocationAndReplacement() throws Exception {
        String raw = UUID.randomUUID().toString();
        RefreshToken original = token(owner, raw, false);
        doThrow(new IllegalStateException("Falha sintética ao assinar"))
                .when(jwt).generateAccessToken(owner.getId());

        refresh(raw).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500));

        assertThat(tokens.findAllByUserId(owner.getId())).hasSize(1);
        assertThat(tokens.findById(original.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void expiredTokenDoesNotRotateOrRevokeAnIndependentSession() throws Exception {
        String raw = UUID.randomUUID().toString();
        RefreshToken expired = token(owner, raw, false);
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        tokens.saveAndFlush(expired);
        RefreshToken sibling = token(owner, UUID.randomUUID().toString(), false);

        refresh(raw).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));

        assertThat(tokens.findAllByUserId(owner.getId())).hasSize(2).noneMatch(RefreshToken::isRevoked);
        assertThat(tokens.findById(sibling.getId()).orElseThrow().isRevoked()).isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String raw) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"" + raw + "\"}"));
    }

    private User user() {
        String suffix = UUID.randomUUID().toString();
        return users.saveAndFlush(new User(suffix, suffix + "@example.test", "Teste", null));
    }

    private RefreshToken token(User user, String raw, boolean revoked) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8)));
        var token = new RefreshToken(user, hash, Instant.now().plusSeconds(600));
        token.setRevoked(revoked);
        return tokens.saveAndFlush(token);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Barreira expirou");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
