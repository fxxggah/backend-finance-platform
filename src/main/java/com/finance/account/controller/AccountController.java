package com.finance.account.controller;

import com.finance.account.dto.request.AccountRequest;
import com.finance.account.dto.response.AccountDeleteResponse;
import com.finance.account.dto.response.AccountListResponse;
import com.finance.account.dto.response.AccountResponse;
import com.finance.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Contas", description = "Gerenciamento de contas bancárias, carteiras e saldos")
@SecurityRequirement(name = "bearer-token")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "Listar contas", description = "Retorna a lista de contas do usuário logado, incluindo o saldo dinâmico de cada uma e o patrimônio total (totalBalance). Permite filtrar ou incluir contas arquivadas.")
    public AccountListResponse list(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "true") boolean includeArchived) {
        return accountService.list(userId(jwt), includeArchived);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar conta por ID", description = "Retorna os detalhes de uma conta específica, juntamente com o seu saldo atual calculado.")
    public AccountResponse getById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return accountService.getById(userId(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar conta", description = "Cria uma nova conta financeira. O nome da conta deve ser único por usuário.")
    public AccountResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountRequest request) {
        return accountService.create(userId(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar conta", description = "Atualiza os dados de uma conta existente. Alterar o saldo inicial (initialBalance) atualizará automaticamente o saldo final calculado.")
    public AccountResponse update(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable Long id,
                                  @Valid @RequestBody AccountRequest request) {
        return accountService.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir ou arquivar conta", description = "Remove uma conta. Se a conta já tiver movimentações (transações), ela será arquivada (soft delete). Se estiver completamente vazia, sofrerá exclusão física (hard delete). Não é possível arquivar/excluir a última conta ativa do usuário.")
    public AccountDeleteResponse delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return accountService.delete(userId(jwt), id);
    }

    // O userId vem sempre do JWT, nunca do payload/URL
    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}