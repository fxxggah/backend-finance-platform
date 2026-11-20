package com.finance.auth.controller;

import com.finance.auth.dto.response.AuthResponse;
import com.finance.auth.dto.request.GoogleLoginRequest;
import com.finance.auth.dto.request.LogoutRequest;
import com.finance.auth.dto.request.RefreshRequest;
import com.finance.auth.dto.response.TokenResponse;
import com.finance.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Autenticação e gerenciamento de sessão")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google")
    @Operation(summary = "Login com Google", description = "Valida o ID Token do Google e retorna access + refresh tokens")
    @ApiResponse(responseCode = "200", description = "Sessão iniciada",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "422", description = "Credencial do Google inválida",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Certificados do Google indisponíveis",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.loginWithGoogle(request.idToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar tokens", description = "Troca o refresh token por um novo par de tokens (rotation). "
            + "Reuso retorna 401 e revoga todos os refresh tokens do usuário, inclusive o sucessor de uma renovação concorrente.")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoga o refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
