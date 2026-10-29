package com.finance.category.controller;

import com.finance.category.dto.request.CategoryRequest;
import com.finance.category.dto.request.CategoryUpdateRequest;
import com.finance.category.dto.response.CategoryResponse;
import com.finance.category.service.CategoryService;
import com.finance.common.enums.TransactionType;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categorias", description = "Gerenciamento de categorias de receitas e despesas")
@SecurityRequirement(name = "bearer-token")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Listar categorias", description = "Retorna a lista de categorias do usuário logado, com suporte a filtro opcional por tipo (INCOME ou EXPENSE).")
    public List<CategoryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) TransactionType type) {
        return categoryService.list(userId(jwt), type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar categoria", description = "Cria uma nova categoria personalizada (com nome, cor, ícone e tipo) vinculada ao usuário.")
    public CategoryResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryRequest request) {
        return categoryService.create(userId(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar categoria", description = "Atualiza os dados de uma categoria existente. O tipo da categoria não pode ser alterado se ela já possuir transações vinculadas.")
    public CategoryResponse update(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable Long id,
                                   @Valid @RequestBody CategoryUpdateRequest request) {
        return categoryService.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir categoria", description = "Exclui uma categoria. Se a categoria já possuir transações, o sistema aplicará um soft delete (ocultando-a de novos lançamentos); se estiver vazia, fará a exclusão física.")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        categoryService.delete(userId(jwt), id);
    }

    // O userId vem sempre do JWT, nunca do payload/URL
    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}