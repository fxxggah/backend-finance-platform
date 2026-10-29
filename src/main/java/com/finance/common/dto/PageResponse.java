package com.finance.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Envelope padrão de paginação: { content, page, size, totalElements, totalPages }.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
