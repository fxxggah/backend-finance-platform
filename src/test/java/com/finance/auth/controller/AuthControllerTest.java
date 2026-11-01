package com.finance.auth.controller;

import com.finance.auth.dto.response.AuthResponse;
import com.finance.auth.dto.response.TokenResponse;
import com.finance.auth.service.AuthService;
import com.finance.user.dto.response.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Os endpoints de /auth são públicos (liberados na SecurityConfig real), por isso os filtros
// de segurança ficam desligados neste slice. O que é testado aqui é o contrato HTTP do controller.
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String GOOGLE_URL = "/api/v1/auth/google";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    // Bodies inválidos para um request com um único campo obrigatório
    private static Stream<Arguments> invalidBodies(String field) {
        return Stream.of(
                Arguments.of("campo ausente", "{}"),
                Arguments.of("campo nulo", "{\"%s\":null}".formatted(field)),
                Arguments.of("campo vazio", "{\"%s\":\"\"}".formatted(field)),
                Arguments.of("campo em branco", "{\"%s\":\"   \"}".formatted(field)),
                Arguments.of("body vazio", ""),
                Arguments.of("JSON malformado", "{\"%s\":".formatted(field)));
    }

    static Stream<Arguments> invalidGoogleBodies() {
        return invalidBodies("idToken");
    }

    static Stream<Arguments> invalidRefreshBodies() {
        return invalidBodies("refreshToken");
    }

    // ---------- POST /api/v1/auth/google ----------

    @Test
    @DisplayName("POST /google: deve retornar 200 com tokens e dados do usuário")
    void loginWithGoogle_shouldReturnOk() throws Exception {
        AuthResponse response = new AuthResponse("access-token", "refresh-token", 900L,
                new UserResponse(1L, "João Silva", "joao@example.com", "https://img.example.com/joao.png"));
        when(authService.loginWithGoogle("google-id-token")).thenReturn(response);

        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.name").value("João Silva"))
                .andExpect(jsonPath("$.user.email").value("joao@example.com"))
                .andExpect(jsonPath("$.user.pictureUrl").value("https://img.example.com/joao.png"));

        verify(authService).loginWithGoogle("google-id-token");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidGoogleBodies")
    @DisplayName("POST /google: deve retornar 400 para body inválido")
    void loginWithGoogle_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /google: deve retornar 401 quando o service rejeitar as credenciais")
    void loginWithGoogle_shouldReturnUnauthorized_whenCredentialsAreRejected() throws Exception {
        when(authService.loginWithGoogle("token-invalido"))
                .thenThrow(new BadCredentialsException("Conta de usuário desativada"));

        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"token-invalido\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /api/v1/auth/refresh ----------

    @Test
    @DisplayName("POST /refresh: deve retornar 200 com o novo par de tokens")
    void refresh_shouldReturnOk() throws Exception {
        when(authService.refresh("old-refresh"))
                .thenReturn(new TokenResponse("new-access", "new-refresh", 900L));

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.expiresIn").value(900));

        verify(authService).refresh("old-refresh");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRefreshBodies")
    @DisplayName("POST /refresh: deve retornar 400 para body inválido")
    void refresh_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /refresh: deve retornar 401 quando o refresh token for inválido, expirado ou reutilizado")
    void refresh_shouldReturnUnauthorized_whenTokenIsRejected() throws Exception {
        when(authService.refresh("invalido"))
                .thenThrow(new BadCredentialsException("Refresh token inválido ou já utilizado"));

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalido\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /api/v1/auth/logout ----------

    @Test
    @DisplayName("POST /logout: deve retornar 204 sem corpo e revogar o refresh token informado")
    void logout_shouldReturnNoContent() throws Exception {
        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"my-refresh\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(authService).logout("my-refresh");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRefreshBodies")
    @DisplayName("POST /logout: deve retornar 400 para body inválido")
    void logout_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }
}