package com.finance.transfer.controller;

import com.finance.common.dto.PageResponse;
import com.finance.transfer.dto.request.TransferRequest;
import com.finance.transfer.dto.response.TransferResponse;
import com.finance.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "Transferências", description = "Movimentações financeiras entre contas do usuário")
@SecurityRequirement(name = "bearer-token")
public class TransferController {

    private final TransferService transferService;

    @GetMapping
    @Operation(summary = "Listar transferências", description = "Retorna uma lista paginada de transferências. Pode ser filtrada por período ou por conta específica.")
    public PageResponse<TransferResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @ParameterObject @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {

        return transferService.list(userId(jwt), from, to, accountId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar transferência por ID")
    public TransferResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return transferService.findById(userId(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar transferência", description = "Transfere um valor de uma conta para outra. Não gera receitas ou despesas, apenas altera o saldo dinâmico das contas.")
    public TransferResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransferRequest request) {
        return transferService.create(userId(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar transferência", description = "Recalcula automaticamente os saldos das contas envolvidas (as antigas e as novas).")
    public TransferResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody TransferRequest request) {
        return transferService.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir transferência", description = "Remove a transferência e recalcula os saldos das contas automaticamente.")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        transferService.delete(userId(jwt), id);
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}