package com.finance.transaction.controller;

import com.finance.common.enums.TransactionType;
import com.finance.common.dto.PageResponse;
import com.finance.transaction.dto.request.TransactionFilter;
import com.finance.transaction.dto.request.TransactionRequest;
import com.finance.transaction.dto.response.TransactionResponse;
import com.finance.transaction.service.TransactionService;
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
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transações", description = "Gerenciamento de receitas e despesas")
@SecurityRequirement(name = "bearer-token")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @Operation(summary = "Listar transações", description = "Retorna uma lista paginada de transações do usuário logado, com suporte a filtros dinâmicos.")
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "type", required = false) TransactionType type,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "accountId", required = false) Long accountId,
            @RequestParam(name = "creditCardId", required = false) Long creditCardId,
            @RequestParam(name = "q", required = false) String q,
            @ParameterObject
            @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {

        TransactionFilter filter = new TransactionFilter(from, to, type, categoryId, accountId, creditCardId, q);
        return transactionService.list(userId(jwt), filter, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar transação por ID", description = "Retorna os detalhes de uma transação específica.")
    public TransactionResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return transactionService.findById(userId(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar transação", description = "Cria uma nova transação (receita ou despesa) vinculada a uma conta ou cartão.")
    public TransactionResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransactionRequest request) {
        return transactionService.create(userId(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar transação", description = "Atualiza os dados de uma transação existente. Não permite alterar o valor ou parcelas se for uma compra parcelada.")
    public TransactionResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir transação", description = "Realiza a exclusão (soft delete) de uma transação. Se for parcelada, exclui o grupo inteiro.")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        transactionService.delete(userId(jwt), id);
    }

    /**
     * O userId vem sempre do JWT (subject), nunca do payload ou da URL.
     */
    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}