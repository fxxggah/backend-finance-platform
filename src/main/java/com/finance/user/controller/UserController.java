package com.finance.user.controller;

import com.finance.security.CurrentUser;
import com.finance.user.dto.response.UserResponse;
import com.finance.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuário", description = "Perfil do usuário autenticado")
@SecurityRequirement(name = "bearer-token")
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;

    @GetMapping("/me")
    @Operation(summary = "Dados do usuário logado")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.get(currentUser.id(jwt)));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Excluir conta (LGPD)", description = "Apaga a conta e todos os dados do usuário")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Jwt jwt) {
        userService.delete(currentUser.id(jwt));
        return ResponseEntity.noContent().build();
    }
}
