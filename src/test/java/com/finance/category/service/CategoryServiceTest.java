package com.finance.category.service;

import com.finance.category.dto.request.CategoryRequest;
import com.finance.category.dto.request.CategoryUpdateRequest;
import com.finance.category.dto.response.CategoryResponse;
import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.enums.TransactionType;
import com.finance.common.exception.ConflictException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.goal.repository.GoalRepository;
import com.finance.transaction.repository.TransactionRepository;
import com.finance.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CATEGORY_ID = 10L;
    private static final TransactionType TYPE = TransactionType.EXPENSE;

    private static final String NOT_FOUND = "Categoria não encontrada";
    private static final String CONFLICT = "Já existe uma categoria com esse nome para esse tipo";

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private GoalRepository goalRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CategoryService categoryService;

    // ---------- helpers ----------

    private Category category(Long id, String name, TransactionType type, boolean deleted) {
        Category category = new Category(
                new User("google-1", "joao@example.com", "João", null),
                name, type, "#3B82F6", "tag");
        category.setId(id);
        category.setDeleted(deleted);
        return category;
    }

    private void stubFindOwned(Category category) {
        when(categoryRepository.findByIdAndUserIdAndDeletedFalse(category.getId(), USER_ID))
                .thenReturn(Optional.of(category));
    }

    // ---------- list ----------

    @Test
    @DisplayName("list: sem filtro de tipo deve retornar todas as categorias ativas do usuário")
    void list_withoutType_shouldReturnAllActiveCategories() {
        when(categoryRepository.findAllByUserIdAndDeletedFalse(USER_ID)).thenReturn(List.of(
                category(1L, "Mercado", TransactionType.EXPENSE, false),
                category(2L, "Salário", TransactionType.INCOME, false)));

        List<CategoryResponse> response = categoryService.list(USER_ID, null);

        assertThat(response).hasSize(2);
        assertThat(response).extracting(CategoryResponse::name).containsExactly("Mercado", "Salário");
        assertThat(response).extracting(CategoryResponse::type)
                .containsExactly(TransactionType.EXPENSE, TransactionType.INCOME);
        verify(categoryRepository, never()).findAllByUserIdAndTypeAndDeletedFalse(anyLong(), any());
    }

    @Test
    @DisplayName("list: com filtro de tipo deve usar a query filtrada")
    void list_withType_shouldQueryByType() {
        when(categoryRepository.findAllByUserIdAndTypeAndDeletedFalse(USER_ID, TransactionType.INCOME))
                .thenReturn(List.of(category(2L, "Salário", TransactionType.INCOME, false)));

        List<CategoryResponse> response = categoryService.list(USER_ID, TransactionType.INCOME);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).type()).isEqualTo(TransactionType.INCOME);
        assertThat(response.get(0).color()).isEqualTo("#3B82F6");
        assertThat(response.get(0).icon()).isEqualTo("tag");
        verify(categoryRepository, never()).findAllByUserIdAndDeletedFalse(anyLong());
    }

    @Test
    @DisplayName("list: deve retornar lista vazia quando o usuário não tem categorias")
    void list_shouldReturnEmpty_whenUserHasNoCategories() {
        when(categoryRepository.findAllByUserIdAndDeletedFalse(USER_ID)).thenReturn(List.of());

        assertThat(categoryService.list(USER_ID, null)).isEmpty();
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: deve remover espaços do nome e persistir a nova categoria vinculada ao usuário")
    void create_shouldTrimNameAndPersistCategory() {
        CategoryRequest request = new CategoryRequest("  Mercado  ", TYPE, "#8A05BE", "shopping-cart");
        User userRef = new User("google-1", "joao@example.com", "João", null);
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndType(USER_ID, "Mercado", TYPE))
                .thenReturn(Optional.empty());
        when(entityManager.getReference(User.class, USER_ID)).thenReturn(userRef);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(CATEGORY_ID);
            return saved;
        });

        CategoryResponse response = categoryService.create(USER_ID, request);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        Category persisted = captor.getValue();
        assertThat(persisted.getUser()).isSameAs(userRef);
        assertThat(persisted.getName()).isEqualTo("Mercado");
        assertThat(persisted.getType()).isEqualTo(TYPE);
        assertThat(persisted.getColor()).isEqualTo("#8A05BE");
        assertThat(persisted.getIcon()).isEqualTo("shopping-cart");
        assertThat(persisted.isDeleted()).isFalse();

        assertThat(response.id()).isEqualTo(CATEGORY_ID);
        assertThat(response.name()).isEqualTo("Mercado");
        assertThat(response.type()).isEqualTo(TYPE);
    }

    @Test
    @DisplayName("create: deve aceitar cor e ícone nulos")
    void create_shouldAcceptNullColorAndIcon() {
        CategoryRequest request = new CategoryRequest("Mercado", TYPE, null, null);
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndType(USER_ID, "Mercado", TYPE))
                .thenReturn(Optional.empty());
        when(entityManager.getReference(User.class, USER_ID))
                .thenReturn(new User("google-1", "joao@example.com", "João", null));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(CATEGORY_ID);
            return saved;
        });

        CategoryResponse response = categoryService.create(USER_ID, request);

        assertThat(response.color()).isNull();
        assertThat(response.icon()).isNull();
    }

    @Test
    @DisplayName("create: deve lançar ConflictException quando já existe categoria ativa com o mesmo nome e tipo")
    void create_shouldThrowConflict_whenActiveCategoryAlreadyExists() {
        CategoryRequest request = new CategoryRequest(" Mercado ", TYPE, null, null);
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndType(USER_ID, "Mercado", TYPE))
                .thenReturn(Optional.of(category(CATEGORY_ID, "Mercado", TYPE, false)));

        assertThatThrownBy(() -> categoryService.create(USER_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage(CONFLICT);

        verify(categoryRepository, never()).save(any(Category.class));
        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("create: deve reativar a categoria excluída com o mesmo nome e tipo atualizando seus dados")
    void create_shouldReactivateDeletedCategory() {
        Category deleted = category(CATEGORY_ID, "Mercado", TYPE, true);
        CategoryRequest request = new CategoryRequest("mercado", TYPE, "#FFFFFF", "novo-icone");
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndType(USER_ID, "mercado", TYPE))
                .thenReturn(Optional.of(deleted));
        when(categoryRepository.save(deleted)).thenReturn(deleted);

        CategoryResponse response = categoryService.create(USER_ID, request);

        assertThat(deleted.isDeleted()).isFalse();
        assertThat(deleted.getName()).isEqualTo("mercado");
        assertThat(deleted.getColor()).isEqualTo("#FFFFFF");
        assertThat(deleted.getIcon()).isEqualTo("novo-icone");
        assertThat(response.id()).isEqualTo(CATEGORY_ID);
        assertThat(response.name()).isEqualTo("mercado");
        verifyNoInteractions(entityManager);
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: deve atualizar nome, cor e ícone mantendo o tipo")
    void update_shouldUpdateFieldsKeepingType() {
        Category category = category(CATEGORY_ID, "Mercado", TYPE, false);
        CategoryUpdateRequest request = new CategoryUpdateRequest("  Supermercado ", "#000000", "store");
        stubFindOwned(category);
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndType(USER_ID, "Supermercado", TYPE))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.update(USER_ID, CATEGORY_ID, request);

        assertThat(category.getName()).isEqualTo("Supermercado");
        assertThat(category.getColor()).isEqualTo("#000000");
        assertThat(category.getIcon()).isEqualTo("store");
        assertThat(category.getType()).isEqualTo(TYPE);

        assertThat(response.id()).isEqualTo(CATEGORY_ID);
        assertThat(response.name()).isEqualTo("Supermercado");
        assertThat(response.type()).isEqualTo(TYPE);
    }

    @Test
    @DisplayName("update: deve permitir manter o próprio nome (trocando apenas cor e ícone)")
    void update_shouldAllowKeepingOwnName() {
        Category category = category(CATEGORY_ID, "Mercado", TYPE, false);
        CategoryUpdateRequest request = new CategoryUpdateRequest("mercado", "#111111", "cart");
        stubFindOwned(category);
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndType(USER_ID, "mercado", TYPE))
                .thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.update(USER_ID, CATEGORY_ID, request);

        assertThat(response.color()).isEqualTo("#111111");
        assertThat(response.icon()).isEqualTo("cart");
        verify(categoryRepository).save(category);
    }

    @Test
    @DisplayName("update: deve aceitar cor e ícone nulos")
    void update_shouldAcceptNullColorAndIcon() {
        Category category = category(CATEGORY_ID, "Mercado", TYPE, false);
        stubFindOwned(category);
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndType(USER_ID, "Mercado", TYPE))
                .thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.update(USER_ID, CATEGORY_ID,
                new CategoryUpdateRequest("Mercado", null, null));

        assertThat(category.getColor()).isNull();
        assertThat(category.getIcon()).isNull();
        assertThat(response.color()).isNull();
        assertThat(response.icon()).isNull();
    }

    @Test
    @DisplayName("update: deve lançar ResourceNotFoundException quando a categoria não existe, está excluída ou é de outro usuário")
    void update_shouldThrowNotFound_whenNotOwnedOrDeleted() {
        when(categoryRepository.findByIdAndUserIdAndDeletedFalse(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(USER_ID, CATEGORY_ID,
                new CategoryUpdateRequest("Mercado", null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND);

        verify(categoryRepository, never()).findByUserIdAndNameIgnoreCaseAndType(anyLong(), anyString(), any());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("update: deve lançar ConflictException quando outra categoria ativa já usa o nome e não alterar a categoria")
    void update_shouldThrowConflict_whenAnotherCategoryHasTheName() {
        Category category = category(CATEGORY_ID, "Mercado", TYPE, false);
        Category other = category(11L, "Transporte", TYPE, false);
        stubFindOwned(category);
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndType(USER_ID, "Transporte", TYPE))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> categoryService.update(USER_ID, CATEGORY_ID,
                new CategoryUpdateRequest("Transporte", "#000000", "bus")))
                .isInstanceOf(ConflictException.class)
                .hasMessage(CONFLICT);

        assertThat(category.getName()).isEqualTo("Mercado");
        assertThat(category.getColor()).isEqualTo("#3B82F6");
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("update: deve lançar ConflictException também quando o nome pertence a uma categoria excluída (unique do banco)")
    void update_shouldThrowConflict_whenNameBelongsToDeletedCategory() {
        Category category = category(CATEGORY_ID, "Mercado", TYPE, false);
        Category deletedOther = category(11L, "Transporte", TYPE, true);
        stubFindOwned(category);
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndType(USER_ID, "Transporte", TYPE))
                .thenReturn(Optional.of(deletedOther));

        assertThatThrownBy(() -> categoryService.update(USER_ID, CATEGORY_ID,
                new CategoryUpdateRequest("Transporte", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(CONFLICT);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: deve excluir fisicamente a categoria sem transações nem metas")
    void delete_shouldHardDelete_whenCategoryHasNoTransactionsNorGoals() {
        Category category = category(CATEGORY_ID, "Mercado", TYPE, false);
        stubFindOwned(category);
        when(transactionRepository.existsByCategoryIdAndDeletedFalse(CATEGORY_ID)).thenReturn(false);
        when(goalRepository.existsByCategoryId(CATEGORY_ID)).thenReturn(false);

        categoryService.delete(USER_ID, CATEGORY_ID);

        assertThat(category.isDeleted()).isFalse();
        verify(categoryRepository).delete(category);
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("delete: deve fazer soft delete quando a categoria possui transações")
    void delete_shouldSoftDelete_whenCategoryHasTransactions() {
        Category category = category(CATEGORY_ID, "Mercado", TYPE, false);
        stubFindOwned(category);
        when(transactionRepository.existsByCategoryIdAndDeletedFalse(CATEGORY_ID)).thenReturn(true);

        categoryService.delete(USER_ID, CATEGORY_ID);

        assertThat(category.isDeleted()).isTrue();
        verify(categoryRepository).save(category);
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    @DisplayName("delete: deve fazer soft delete quando a categoria possui metas")
    void delete_shouldSoftDelete_whenCategoryHasGoals() {
        Category category = category(CATEGORY_ID, "Mercado", TYPE, false);
        stubFindOwned(category);
        when(transactionRepository.existsByCategoryIdAndDeletedFalse(CATEGORY_ID)).thenReturn(false);
        when(goalRepository.existsByCategoryId(CATEGORY_ID)).thenReturn(true);

        categoryService.delete(USER_ID, CATEGORY_ID);

        assertThat(category.isDeleted()).isTrue();
        verify(categoryRepository).save(category);
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    @DisplayName("delete: deve lançar ResourceNotFoundException quando a categoria não existe, está excluída ou é de outro usuário")
    void delete_shouldThrowNotFound_whenNotOwnedOrDeleted() {
        when(categoryRepository.findByIdAndUserIdAndDeletedFalse(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(USER_ID, CATEGORY_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND);

        verify(categoryRepository, never()).delete(any(Category.class));
        verify(categoryRepository, never()).save(any(Category.class));
        verifyNoInteractions(transactionRepository, goalRepository);
    }
}