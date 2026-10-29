package com.finance.category.service;

import com.finance.category.dto.request.CategoryRequest;
import com.finance.category.dto.request.CategoryUpdateRequest;
import com.finance.category.dto.response.CategoryResponse;
import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.enums.TransactionType;
import com.finance.common.exception.ConflictException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final String DUPLICATE_MESSAGE = "Já existe uma categoria com esse nome para esse tipo";

    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId, TransactionType type) {
        List<Category> categories = type != null
                ? categoryRepository.findAllByUserIdAndTypeAndDeletedFalse(userId, type)
                : categoryRepository.findAllByUserIdAndDeletedFalse(userId);

        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        String name = request.name().trim();

        Optional<Category> existing = categoryRepository
                .findByUserIdAndNameIgnoreCaseAndType(userId, name, request.type());

        if (existing.isPresent()) {
            Category category = existing.get();

            if (!category.isDeleted()) {
                throw new ConflictException(DUPLICATE_MESSAGE);
            }

            // Categoria excluída (soft delete) com o mesmo nome/tipo: reativa em vez de
            // violar a unique do banco. O histórico de transações antigas volta a apontar
            // para uma categoria ativa, o que é o comportamento esperado.
            category.setDeleted(false);
            category.setName(name);
            category.setColor(request.color());
            category.setIcon(request.icon());
            return CategoryResponse.from(categoryRepository.save(category));
        }

        User userRef = entityManager.getReference(User.class, userId);
        Category category = new Category(userRef, name, request.type(), request.color(), request.icon());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long userId, Long id, CategoryUpdateRequest request) {
        Category category = findOwned(userId, id);
        String name = request.name().trim();

        // Também considera categorias excluídas: a unique do banco as inclui
        categoryRepository.findByUserIdAndNameIgnoreCaseAndType(userId, name, category.getType())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException(DUPLICATE_MESSAGE);
                });

        category.setName(name);
        category.setColor(request.color());
        category.setIcon(request.icon());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    /**
     * Categoria com transações: soft delete (some do seletor, histórico mantém o nome).
     * Categoria sem transações: hard delete.
     */
    @Transactional
    public void delete(Long userId, Long id) {
        Category category = findOwned(userId, id);

        if (hasTransactions(category)) {
            category.setDeleted(true);
            categoryRepository.save(category);
            return;
        }

        categoryRepository.delete(category);
    }

    // Categoria de outro usuário ou já excluída: 404 (não vaza a existência do ID)
    private Category findOwned(Long userId, Long id) {
        return categoryRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
    }

    /**
     * TODO (quando existirem Transaction e Goal): retornar true se houver transações
     * (ou metas) ligadas à categoria. Por enquanto sempre false, então o DELETE exclui de vez.
     */
    private boolean hasTransactions(Category category) {
        return false;
    }
}