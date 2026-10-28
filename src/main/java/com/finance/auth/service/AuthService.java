package com.finance.auth.service;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.auth.dto.response.AuthResponse;
import com.finance.auth.dto.response.TokenResponse;
import com.finance.auth.entity.RefreshToken;
import com.finance.auth.repository.RefreshTokenRepository;
import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.enums.AccountType;
import com.finance.common.enums.TransactionType;
import com.finance.security.GoogleTokenVerifier;
import com.finance.security.GoogleTokenVerifier.GoogleClaims;
import com.finance.security.JwtService;
import com.finance.user.dto.response.UserResponse;
import com.finance.user.entity.User;
import com.finance.user.mapper.UserMapper;
import com.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtService jwtService;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // ---------------------------------------------------------------------------
    // Login com Google
    // ---------------------------------------------------------------------------

    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleClaims claims = googleTokenVerifier.verify(idToken);

        User user = userRepository.findByGoogleId(claims.googleId())
                .map(existing -> updateUser(existing, claims))
                .orElseGet(() -> createUserWithSeed(claims));

        if (!user.isActive()) {
            throw new BadCredentialsException("Conta de usuário desativada");
        }

        String rawRefresh = generateRawToken();
        saveRefreshToken(user, rawRefresh);

        UserResponse userResponse = UserMapper.toResponse(user);
        return new AuthResponse(
                jwtService.generateAccessToken(user.getId()),
                rawRefresh,
                jwtService.getAccessTokenExpiration(),
                userResponse);
    }

    // ---------------------------------------------------------------------------
    // Refresh com rotation
    // ---------------------------------------------------------------------------

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido ou já utilizado"));

        if (stored.isRevoked()) {
            // Detecta reutilização: revoga todos os tokens do usuário (sinal de
            // comprometimento)
            refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
            throw new BadCredentialsException("Refresh token reutilizado. Faça login novamente");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token expirado");
        }

        if (!stored.getUser().isActive()) {
            throw new BadCredentialsException("Conta de usuário desativada");
        }

        // Rotation: revoga o antigo
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        // Emite o novo
        String newRaw = generateRawToken();
        saveRefreshToken(stored.getUser(), newRaw);

        return new TokenResponse(
                jwtService.generateAccessToken(stored.getUser().getId()),
                newRaw,
                jwtService.getAccessTokenExpiration());
    }

    // ---------------------------------------------------------------------------
    // Logout
    // ---------------------------------------------------------------------------

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
        // Se o token não existir, ignora silenciosamente (idempotente)
    }

    // ---------------------------------------------------------------------------
    // Helpers privados
    // ---------------------------------------------------------------------------

    private User updateUser(User user, GoogleClaims claims) {
        user.setName(claims.name());
        user.setPictureUrl(claims.pictureUrl());
        return userRepository.save(user);
    }

    private User createUserWithSeed(GoogleClaims claims) {
        User user = new User(claims.googleId(), claims.email(), claims.name(), claims.pictureUrl());
        user = userRepository.save(user);

        createDefaultCategories(user);
        createDefaultAccount(user);

        return user;
    }

    private void createDefaultCategories(User user) {
        List<Category> defaults = List.of(
                // Despesas
                new Category(user, "Alimentação", TransactionType.EXPENSE, "#EF4444", "utensils"),
                new Category(user, "Transporte", TransactionType.EXPENSE, "#F97316", "car"),
                new Category(user, "Moradia", TransactionType.EXPENSE, "#EAB308", "home"),
                new Category(user, "Saúde", TransactionType.EXPENSE, "#22C55E", "heart-pulse"),
                new Category(user, "Educação", TransactionType.EXPENSE, "#3B82F6", "book-open"),
                new Category(user, "Lazer", TransactionType.EXPENSE, "#A855F7", "gamepad-2"),
                new Category(user, "Compras", TransactionType.EXPENSE, "#EC4899", "shopping-bag"),
                new Category(user, "Outros", TransactionType.EXPENSE, "#6B7280", "ellipsis"),
                // Receitas
                new Category(user, "Salário", TransactionType.INCOME, "#22C55E", "briefcase"),
                new Category(user, "Freelance", TransactionType.INCOME, "#3B82F6", "laptop"),
                new Category(user, "Investimentos", TransactionType.INCOME, "#F59E0B", "trending-up"),
                new Category(user, "Outros", TransactionType.INCOME, "#6B7280", "ellipsis"));
        categoryRepository.saveAll(defaults);
    }

    private void createDefaultAccount(User user) {
        Account wallet = new Account(user, "Carteira", AccountType.CASH, BigDecimal.ZERO, "#6B7280", "wallet");
        accountRepository.save(wallet);
    }

    private void saveRefreshToken(User user, String rawToken) {
        Instant expiresAt = Instant.now().plusSeconds(refreshTokenExpiration);
        RefreshToken token = new RefreshToken(user, hashToken(rawToken), expiresAt);
        refreshTokenRepository.save(token);
    }

    private String generateRawToken() {
        return UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "");
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }
}
