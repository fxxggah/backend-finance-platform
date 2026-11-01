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
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String ID_TOKEN = "google-id-token";
    private static final Long USER_ID = 1L;
    private static final String ACCESS_TOKEN = "access-token";
    private static final long ACCESS_EXPIRATION = 900L;
    private static final long REFRESH_EXPIRATION_SECONDS = 3600L;

    // SHA-256("abc") em hexadecimal: valor fixo para validar o hash independente da implementação
    private static final String RAW_ABC = "abc";
    private static final String HASH_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private static final String MSG_INACTIVE = "Conta de usuário desativada";
    private static final String MSG_INVALID = "Refresh token inválido ou já utilizado";
    private static final String MSG_REUSED = "Refresh token reutilizado. Faça login novamente";
    private static final String MSG_EXPIRED = "Refresh token expirado";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private GoogleTokenVerifier googleTokenVerifier;
    @Mock
    private JwtService jwtService;

    @Captor
    private ArgumentCaptor<List<Category>> categoriesCaptor;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Campo injetado por @Value, fora do construtor
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", REFRESH_EXPIRATION_SECONDS);
    }

    // ---------- helpers ----------

    private GoogleClaims claims(String googleId, String email, String name, String pictureUrl) {
        GoogleClaims claims = mock(GoogleClaims.class);
        lenient().when(claims.googleId()).thenReturn(googleId);
        lenient().when(claims.email()).thenReturn(email);
        lenient().when(claims.name()).thenReturn(name);
        lenient().when(claims.pictureUrl()).thenReturn(pictureUrl);
        return claims;
    }

    private User existingUser() {
        User user = new User("google-123", "joao@example.com", "Nome Antigo", "https://img.example.com/old.png");
        user.setId(USER_ID);
        return user;
    }

    private void stubUserSave() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(USER_ID);
            }
            return saved;
        });
    }

    private void stubJwt() {
        when(jwtService.generateAccessToken(USER_ID)).thenReturn(ACCESS_TOKEN);
        when(jwtService.getAccessTokenExpiration()).thenReturn(ACCESS_EXPIRATION);
    }

    private RefreshToken storedToken(User user, String tokenHash, Instant expiresAt, boolean revoked) {
        RefreshToken token = new RefreshToken(user, tokenHash, expiresAt);
        token.setRevoked(revoked);
        return token;
    }

    private static String sha256(String raw) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- loginWithGoogle: primeiro acesso ----------

    @Test
    @DisplayName("loginWithGoogle: primeiro acesso deve criar o usuário com os dados do Google")
    void loginWithGoogle_firstLogin_shouldCreateUser() {
        GoogleClaims claims = claims("google-123", "joao@example.com", "João Silva", "https://img.example.com/joao.png");
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(claims);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        stubUserSave();
        stubJwt();

        authService.loginWithGoogle(ID_TOKEN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getGoogleId()).isEqualTo("google-123");
        assertThat(created.getEmail()).isEqualTo("joao@example.com");
        assertThat(created.getName()).isEqualTo("João Silva");
        assertThat(created.getPictureUrl()).isEqualTo("https://img.example.com/joao.png");
        assertThat(created.isActive()).isTrue();
    }

    @Test
    @DisplayName("loginWithGoogle: primeiro acesso deve criar as 12 categorias padrão do usuário")
    void loginWithGoogle_firstLogin_shouldCreateDefaultCategories() {
        GoogleClaims claims = claims("google-123", "joao@example.com", "João Silva", null);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(claims);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        stubUserSave();
        stubJwt();

        authService.loginWithGoogle(ID_TOKEN);

        verify(categoryRepository).saveAll(categoriesCaptor.capture());
        List<Category> categories = categoriesCaptor.getValue();

        assertThat(categories).hasSize(12);
        assertThat(categories).extracting("name", "type").containsExactlyInAnyOrder(
                tuple("Alimentação", TransactionType.EXPENSE),
                tuple("Transporte", TransactionType.EXPENSE),
                tuple("Moradia", TransactionType.EXPENSE),
                tuple("Saúde", TransactionType.EXPENSE),
                tuple("Educação", TransactionType.EXPENSE),
                tuple("Lazer", TransactionType.EXPENSE),
                tuple("Compras", TransactionType.EXPENSE),
                tuple("Outros", TransactionType.EXPENSE),
                tuple("Salário", TransactionType.INCOME),
                tuple("Freelance", TransactionType.INCOME),
                tuple("Investimentos", TransactionType.INCOME),
                tuple("Outros", TransactionType.INCOME));
        assertThat(categories).extracting("user.id").containsOnly(USER_ID);
    }

    @Test
    @DisplayName("loginWithGoogle: primeiro acesso deve criar a conta padrão 'Carteira'")
    void loginWithGoogle_firstLogin_shouldCreateDefaultAccount() {
        GoogleClaims claims = claims("google-123", "joao@example.com", "João Silva", null);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(claims);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        stubUserSave();
        stubJwt();

        authService.loginWithGoogle(ID_TOKEN);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account wallet = captor.getValue();
        assertThat(wallet.getUser().getId()).isEqualTo(USER_ID);
        assertThat(wallet.getName()).isEqualTo("Carteira");
        assertThat(wallet.getType()).isEqualTo(AccountType.CASH);
        assertThat(wallet.getInitialBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wallet.getColor()).isEqualTo("#6B7280");
        assertThat(wallet.getIcon()).isEqualTo("wallet");
        assertThat(wallet.isArchived()).isFalse();
    }

    @Test
    @DisplayName("loginWithGoogle: deve retornar access token, refresh token em texto puro e o usuário")
    void loginWithGoogle_shouldReturnTokensAndUser() {
        GoogleClaims claims = claims("google-123", "joao@example.com", "João Silva", "https://img.example.com/joao.png");
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(claims);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        stubUserSave();
        stubJwt();

        AuthResponse response = authService.loginWithGoogle(ID_TOKEN);

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.expiresIn()).isEqualTo(ACCESS_EXPIRATION);
        assertThat(response.refreshToken()).matches("[0-9a-f]{64}");
        assertThat(response.user().id()).isEqualTo(USER_ID);
        assertThat(response.user().name()).isEqualTo("João Silva");
        assertThat(response.user().email()).isEqualTo("joao@example.com");
        assertThat(response.user().pictureUrl()).isEqualTo("https://img.example.com/joao.png");
    }

    @Test
    @DisplayName("loginWithGoogle: deve persistir apenas o hash do refresh token, com validade configurada")
    void loginWithGoogle_shouldPersistRefreshTokenHashWithExpiration() {
        GoogleClaims claims = claims("google-123", "joao@example.com", "João Silva", null);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(claims);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        stubUserSave();
        stubJwt();

        Instant before = Instant.now();
        AuthResponse response = authService.loginWithGoogle(ID_TOKEN);
        Instant after = Instant.now();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUser().getId()).isEqualTo(USER_ID);
        assertThat(saved.getTokenHash()).isEqualTo(sha256(response.refreshToken()));
        assertThat(saved.getTokenHash()).isNotEqualTo(response.refreshToken());
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isBetween(
                before.plusSeconds(REFRESH_EXPIRATION_SECONDS),
                after.plusSeconds(REFRESH_EXPIRATION_SECONDS));
    }

    // ---------- loginWithGoogle: usuário existente ----------

    @Test
    @DisplayName("loginWithGoogle: usuário existente deve ter nome e foto atualizados, sem recriar categorias e conta")
    void loginWithGoogle_existingUser_shouldUpdateProfileWithoutSeeding() {
        User existing = existingUser();
        GoogleClaims claims = claims("google-123", "outro@example.com", "Nome Novo", "https://img.example.com/new.png");
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(claims);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));
        stubUserSave();
        stubJwt();

        AuthResponse response = authService.loginWithGoogle(ID_TOKEN);

        assertThat(existing.getName()).isEqualTo("Nome Novo");
        assertThat(existing.getPictureUrl()).isEqualTo("https://img.example.com/new.png");
        assertThat(existing.getEmail()).isEqualTo("joao@example.com"); // email não é sobrescrito
        verify(userRepository).save(existing);
        verifyNoInteractions(categoryRepository, accountRepository);

        assertThat(response.user().name()).isEqualTo("Nome Novo");
        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("loginWithGoogle: usuário existente deve ficar sem foto quando o Google não retorna picture")
    void loginWithGoogle_existingUser_shouldClearPicture_whenGoogleHasNone() {
        User existing = existingUser();
        GoogleClaims claims = claims("google-123", "joao@example.com", "Nome Antigo", null);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(claims);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));
        stubUserSave();
        stubJwt();

        authService.loginWithGoogle(ID_TOKEN);

        assertThat(existing.getPictureUrl()).isNull();
    }

    @Test
    @DisplayName("loginWithGoogle: cada login deve gerar um refresh token diferente")
    void loginWithGoogle_shouldGenerateDifferentRefreshTokenOnEachLogin() {
        User existing = existingUser();
        GoogleClaims claims = claims("google-123", "joao@example.com", "Nome Antigo", null);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(claims);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));
        stubUserSave();
        stubJwt();

        AuthResponse first = authService.loginWithGoogle(ID_TOKEN);
        AuthResponse second = authService.loginWithGoogle(ID_TOKEN);

        assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
    }

    @Test
    @DisplayName("loginWithGoogle: usuário desativado deve lançar BadCredentialsException e não emitir tokens")
    void loginWithGoogle_shouldThrow_whenUserIsInactive() {
        User existing = existingUser();
        existing.setActive(false);
        GoogleClaims claims = claims("google-123", "joao@example.com", "Nome Antigo", null);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(claims);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existing));
        stubUserSave();

        assertThatThrownBy(() -> authService.loginWithGoogle(ID_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(MSG_INACTIVE);

        verifyNoInteractions(refreshTokenRepository, jwtService, categoryRepository, accountRepository);
    }

    @Test
    @DisplayName("loginWithGoogle: deve propagar a exceção quando o ID Token do Google é inválido")
    void loginWithGoogle_shouldPropagate_whenGoogleTokenIsInvalid() {
        BadCredentialsException error = new BadCredentialsException("Token do Google inválido");
        when(googleTokenVerifier.verify(ID_TOKEN)).thenThrow(error);

        assertThatThrownBy(() -> authService.loginWithGoogle(ID_TOKEN)).isSameAs(error);

        verifyNoInteractions(userRepository, refreshTokenRepository, accountRepository, categoryRepository, jwtService);
    }

    // ---------- refresh ----------

    @Test
    @DisplayName("refresh: deve revogar o token antigo e emitir um novo par de tokens (rotation)")
    void refresh_shouldRotateTokens() {
        User user = existingUser();
        RefreshToken stored = storedToken(user, HASH_ABC, Instant.now().plusSeconds(600), false);
        when(refreshTokenRepository.findByTokenHash(HASH_ABC)).thenReturn(Optional.of(stored));
        stubJwt();

        Instant before = Instant.now();
        TokenResponse response = authService.refresh(RAW_ABC);
        Instant after = Instant.now();

        assertThat(stored.isRevoked()).isTrue();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        List<RefreshToken> saved = captor.getAllValues();
        assertThat(saved.get(0)).isSameAs(stored);

        RefreshToken rotated = saved.get(1);
        assertThat(rotated).isNotSameAs(stored);
        assertThat(rotated.getUser()).isSameAs(user);
        assertThat(rotated.isRevoked()).isFalse();
        assertThat(rotated.getTokenHash()).isEqualTo(sha256(response.refreshToken()));
        assertThat(rotated.getExpiresAt()).isBetween(
                before.plusSeconds(REFRESH_EXPIRATION_SECONDS),
                after.plusSeconds(REFRESH_EXPIRATION_SECONDS));

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.expiresIn()).isEqualTo(ACCESS_EXPIRATION);
        assertThat(response.refreshToken()).matches("[0-9a-f]{64}").isNotEqualTo(RAW_ABC);
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
    }

    @Test
    @DisplayName("refresh: deve lançar BadCredentialsException quando o token não existe")
    void refresh_shouldThrow_whenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(HASH_ABC)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(RAW_ABC))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(MSG_INVALID);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("refresh: token já revogado indica reuso e deve revogar todos os tokens do usuário")
    void refresh_shouldRevokeAllUserTokens_whenTokenIsReused() {
        RefreshToken stored = storedToken(existingUser(), HASH_ABC, Instant.now().plusSeconds(600), true);
        when(refreshTokenRepository.findByTokenHash(HASH_ABC)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(RAW_ABC))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(MSG_REUSED);

        verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("refresh: a detecção de reuso tem prioridade sobre a expiração")
    void refresh_shouldDetectReuse_evenWhenTokenIsAlsoExpired() {
        RefreshToken stored = storedToken(existingUser(), HASH_ABC, Instant.now().minusSeconds(60), true);
        when(refreshTokenRepository.findByTokenHash(HASH_ABC)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(RAW_ABC))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(MSG_REUSED);

        verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
    }

    @Test
    @DisplayName("refresh: token expirado deve lançar BadCredentialsException sem emitir novos tokens")
    void refresh_shouldThrow_whenTokenIsExpired() {
        RefreshToken stored = storedToken(existingUser(), HASH_ABC, Instant.now().minusSeconds(1), false);
        when(refreshTokenRepository.findByTokenHash(HASH_ABC)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(RAW_ABC))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(MSG_EXPIRED);

        assertThat(stored.isRevoked()).isFalse();
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("refresh: usuário desativado deve lançar BadCredentialsException sem emitir novos tokens")
    void refresh_shouldThrow_whenUserIsInactive() {
        User user = existingUser();
        user.setActive(false);
        RefreshToken stored = storedToken(user, HASH_ABC, Instant.now().plusSeconds(600), false);
        when(refreshTokenRepository.findByTokenHash(HASH_ABC)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(RAW_ABC))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(MSG_INACTIVE);

        assertThat(stored.isRevoked()).isFalse();
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verifyNoInteractions(jwtService);
    }

    // ---------- logout ----------

    @Test
    @DisplayName("logout: deve revogar o token informado")
    void logout_shouldRevokeToken() {
        RefreshToken stored = storedToken(existingUser(), HASH_ABC, Instant.now().plusSeconds(600), false);
        when(refreshTokenRepository.findByTokenHash(HASH_ABC)).thenReturn(Optional.of(stored));

        authService.logout(RAW_ABC);

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    @DisplayName("logout: deve ser idempotente e ignorar silenciosamente token inexistente")
    void logout_shouldBeIdempotent_whenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(HASH_ABC)).thenReturn(Optional.empty());

        authService.logout(RAW_ABC);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
    }
}