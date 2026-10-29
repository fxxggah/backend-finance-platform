package com.finance.account.controller;

import com.finance.account.dto.request.AccountRequest;
import com.finance.account.dto.response.AccountDeleteResponse;
import com.finance.account.dto.response.AccountListResponse;
import com.finance.account.dto.response.AccountResponse;
import com.finance.account.service.AccountService;
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
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public AccountListResponse list(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "true") boolean includeArchived) {
        return accountService.list(userId(jwt), includeArchived);
    }

    @GetMapping("/{id}")
    public AccountResponse getById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return accountService.getById(userId(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountRequest request) {
        return accountService.create(userId(jwt), request);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable Long id,
                                  @Valid @RequestBody AccountRequest request) {
        return accountService.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public AccountDeleteResponse delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return accountService.delete(userId(jwt), id);
    }

    // O userId vem sempre do JWT, nunca do payload/URL
    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}