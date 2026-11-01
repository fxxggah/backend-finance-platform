package com.finance.category.repository;

import com.finance.category.entity.Category;
import com.finance.common.enums.TransactionType;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryRepositoryTest {

    private static final TransactionType EXPENSE = TransactionType.EXPENSE;
    private static final TransactionType INCOME = TransactionType.INCOME;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(new User("google-owner", "owner@example.com", "Owner", null));
        other = userRepository.saveAndFlush(new User("google-other", "other@example.com", "Other", null));
    }

    private Category persist(User user, String name, TransactionType type, boolean deleted) {
        Category category = new Category(user, name, type, "#3B82F6", "tag");
        category.setDeleted(deleted);
        return categoryRepository.saveAndFlush(category);
    }

    // ---------- persistência ----------

    @Test
    @DisplayName("save: deve persistir categoria com id, auditoria e valores padrão")
    void save_shouldPersistCategoryWithDefaults() {
        Category category = categoryRepository.saveAndFlush(new Category(owner, "Mercado", EXPENSE, null, null));
        entityManager.clear();

        Category reloaded = categoryRepository.findById(category.getId()).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getName()).isEqualTo("Mercado");
        assertThat(reloaded.getType()).isEqualTo(EXPENSE);
        assertThat(reloaded.isDeleted()).isFalse();
        assertThat(reloaded.getColor()).isNull();
        assertThat(reloaded.getIcon()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save: deve preservar cor e ícone")
    void save_shouldKeepColorAndIcon() {
        Category category = persist(owner, "Mercado", EXPENSE, false);
        entityManager.clear();

        Category reloaded = categoryRepository.findById(category.getId()).orElseThrow();

        assertThat(reloaded.getColor()).isEqualTo("#3B82F6");
        assertThat(reloaded.getIcon()).isEqualTo("tag");
    }

    @Test
    @DisplayName("save: deve falhar quando o nome (obrigatório) for nulo")
    void save_shouldFail_whenNameIsNull() {
        Category invalid = new Category(owner, null, EXPENSE, null, null);

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(invalid))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("save: deve falhar quando o tipo (obrigatório) for nulo")
    void save_shouldFail_whenTypeIsNull() {
        Category invalid = new Category(owner, "Mercado", null, null, null);

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(invalid))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("save: deve permitir o mesmo nome com tipo diferente ou para outro usuário")
    void save_shouldAllowSameNameForDifferentTypeOrUser() {
        persist(owner, "Outros", EXPENSE, false);

        Category otherType = persist(owner, "Outros", INCOME, false);
        Category otherUserCategory = persist(other, "Outros", EXPENSE, false);

        assertThat(otherType.getId()).isNotNull();
        assertThat(otherUserCategory.getId()).isNotNull();
    }

    // ---------- findByIdAndUserId ----------

    @Test
    @DisplayName("findByIdAndUserId: deve retornar a categoria do usuário, inclusive excluída")
    void findByIdAndUserId_shouldReturnCategory_whenOwned() {
        Category active = persist(owner, "Mercado", EXPENSE, false);
        Category deleted = persist(owner, "Antiga", EXPENSE, true);

        assertThat(categoryRepository.findByIdAndUserId(active.getId(), owner.getId())).isPresent();
        assertThat(categoryRepository.findByIdAndUserId(deleted.getId(), owner.getId())).isPresent();
    }

    @Test
    @DisplayName("findByIdAndUserId: deve retornar vazio quando a categoria é de outro usuário")
    void findByIdAndUserId_shouldReturnEmpty_whenOwnedByAnotherUser() {
        Category category = persist(owner, "Mercado", EXPENSE, false);

        assertThat(categoryRepository.findByIdAndUserId(category.getId(), other.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId: deve retornar vazio quando a categoria não existe")
    void findByIdAndUserId_shouldReturnEmpty_whenNotExists() {
        assertThat(categoryRepository.findByIdAndUserId(999_999L, owner.getId())).isEmpty();
    }

    // ---------- findByIdAndUserIdAndDeletedFalse ----------

    @Test
    @DisplayName("findByIdAndUserIdAndDeletedFalse: deve retornar somente categorias ativas do usuário")
    void findByIdAndUserIdAndDeletedFalse_shouldReturnOnlyActiveOwned() {
        Category active = persist(owner, "Mercado", EXPENSE, false);
        Category deleted = persist(owner, "Antiga", EXPENSE, true);

        assertThat(categoryRepository.findByIdAndUserIdAndDeletedFalse(active.getId(), owner.getId())).isPresent();
        assertThat(categoryRepository.findByIdAndUserIdAndDeletedFalse(deleted.getId(), owner.getId())).isEmpty();
        assertThat(categoryRepository.findByIdAndUserIdAndDeletedFalse(active.getId(), other.getId())).isEmpty();
    }

    // ---------- findByUserIdAndNameIgnoreCaseAndType ----------

    @Test
    @DisplayName("findByUserIdAndNameIgnoreCaseAndType: deve ignorar maiúsculas/minúsculas e considerar excluídas")
    void findByUserIdAndNameIgnoreCaseAndType_shouldIgnoreCaseAndIncludeDeleted() {
        persist(owner, "Mercado", EXPENSE, true);

        Optional<Category> result = categoryRepository
                .findByUserIdAndNameIgnoreCaseAndType(owner.getId(), "mErCaDo", EXPENSE);

        assertThat(result).isPresent();
        assertThat(result.get().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("findByUserIdAndNameIgnoreCaseAndType: deve retornar vazio para tipo diferente")
    void findByUserIdAndNameIgnoreCaseAndType_shouldReturnEmpty_whenTypeDiffers() {
        persist(owner, "Outros", EXPENSE, false);

        assertThat(categoryRepository
                .findByUserIdAndNameIgnoreCaseAndType(owner.getId(), "Outros", INCOME)).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdAndNameIgnoreCaseAndType: deve considerar apenas categorias do próprio usuário")
    void findByUserIdAndNameIgnoreCaseAndType_shouldNotSeeOtherUsersCategories() {
        persist(other, "Mercado", EXPENSE, false);

        assertThat(categoryRepository
                .findByUserIdAndNameIgnoreCaseAndType(owner.getId(), "Mercado", EXPENSE)).isEmpty();
    }

    // ---------- findAllByUserIdAndDeletedFalse ----------

    @Test
    @DisplayName("findAllByUserIdAndDeletedFalse: deve retornar somente categorias ativas do usuário")
    void findAllByUserIdAndDeletedFalse_shouldReturnOnlyActiveOfUser() {
        persist(owner, "Mercado", EXPENSE, false);
        persist(owner, "Salário", INCOME, false);
        persist(owner, "Antiga", EXPENSE, true);
        persist(other, "De outro", EXPENSE, false);

        List<Category> result = categoryRepository.findAllByUserIdAndDeletedFalse(owner.getId());

        assertThat(result).extracting(Category::getName).containsExactlyInAnyOrder("Mercado", "Salário");
    }

    @Test
    @DisplayName("findAllByUserIdAndDeletedFalse: deve retornar lista vazia quando o usuário não tem categorias")
    void findAllByUserIdAndDeletedFalse_shouldReturnEmpty_whenUserHasNoCategories() {
        assertThat(categoryRepository.findAllByUserIdAndDeletedFalse(owner.getId())).isEmpty();
    }

    // ---------- findAllByUserIdAndTypeAndDeletedFalse ----------

    @Test
    @DisplayName("findAllByUserIdAndTypeAndDeletedFalse: deve filtrar por tipo e ignorar excluídas")
    void findAllByUserIdAndTypeAndDeletedFalse_shouldFilterByTypeAndIgnoreDeleted() {
        persist(owner, "Mercado", EXPENSE, false);
        persist(owner, "Salário", INCOME, false);
        persist(owner, "Antiga", EXPENSE, true);
        persist(other, "De outro", EXPENSE, false);

        List<Category> result = categoryRepository.findAllByUserIdAndTypeAndDeletedFalse(owner.getId(), EXPENSE);

        assertThat(result).extracting(Category::getName).containsExactly("Mercado");
    }

    // ---------- existsByIdAndUserId ----------

    @Test
    @DisplayName("existsByIdAndUserId: deve retornar true somente para o dono, inclusive se excluída")
    void existsByIdAndUserId_shouldReturnTrueOnlyForOwner() {
        Category active = persist(owner, "Mercado", EXPENSE, false);
        Category deleted = persist(owner, "Antiga", EXPENSE, true);

        assertThat(categoryRepository.existsByIdAndUserId(active.getId(), owner.getId())).isTrue();
        assertThat(categoryRepository.existsByIdAndUserId(deleted.getId(), owner.getId())).isTrue();
        assertThat(categoryRepository.existsByIdAndUserId(active.getId(), other.getId())).isFalse();
        assertThat(categoryRepository.existsByIdAndUserId(999_999L, owner.getId())).isFalse();
    }

    // ---------- existsByIdAndUserIdAndDeletedFalse ----------

    @Test
    @DisplayName("existsByIdAndUserIdAndDeletedFalse: deve retornar true somente para categoria ativa do dono")
    void existsByIdAndUserIdAndDeletedFalse_shouldReturnTrueOnlyForActiveOwned() {
        Category active = persist(owner, "Mercado", EXPENSE, false);
        Category deleted = persist(owner, "Antiga", EXPENSE, true);

        assertThat(categoryRepository.existsByIdAndUserIdAndDeletedFalse(active.getId(), owner.getId())).isTrue();
        assertThat(categoryRepository.existsByIdAndUserIdAndDeletedFalse(deleted.getId(), owner.getId())).isFalse();
        assertThat(categoryRepository.existsByIdAndUserIdAndDeletedFalse(active.getId(), other.getId())).isFalse();
    }

    // ---------- existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse ----------

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse: deve ignorar maiúsculas/minúsculas")
    void existsByName_shouldIgnoreCase() {
        persist(owner, "Mercado", EXPENSE, false);

        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse(
                owner.getId(), "Mercado", EXPENSE)).isTrue();
        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse(
                owner.getId(), "MERCADO", EXPENSE)).isTrue();
    }

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse: deve retornar false para excluída, outro tipo ou nome inexistente")
    void existsByName_shouldReturnFalse_whenDeletedOrDifferentTypeOrNotExists() {
        persist(owner, "Mercado", EXPENSE, false);
        persist(owner, "Antiga", EXPENSE, true);

        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse(
                owner.getId(), "Antiga", EXPENSE)).isFalse();
        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse(
                owner.getId(), "Mercado", INCOME)).isFalse();
        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse(
                owner.getId(), "Inexistente", EXPENSE)).isFalse();
    }

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse: deve considerar apenas categorias do próprio usuário")
    void existsByName_shouldNotSeeOtherUsersCategories() {
        persist(other, "Mercado", EXPENSE, false);

        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse(
                owner.getId(), "Mercado", EXPENSE)).isFalse();
    }

    // ---------- existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalseAndIdNot ----------

    @Test
    @DisplayName("existsBy...AndIdNot: deve ignorar a própria categoria")
    void existsByNameAndIdNot_shouldReturnFalse_whenOnlyTheSameCategoryHasTheName() {
        Category category = persist(owner, "Mercado", EXPENSE, false);

        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalseAndIdNot(
                owner.getId(), "mercado", EXPENSE, category.getId())).isFalse();
    }

    @Test
    @DisplayName("existsBy...AndIdNot: deve retornar true quando outra categoria ativa tem o mesmo nome e tipo")
    void existsByNameAndIdNot_shouldReturnTrue_whenAnotherCategoryHasTheName() {
        persist(owner, "Mercado", EXPENSE, false);
        Category second = persist(owner, "Transporte", EXPENSE, false);

        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalseAndIdNot(
                owner.getId(), "MERCADO", EXPENSE, second.getId())).isTrue();
    }

    @Test
    @DisplayName("existsBy...AndIdNot: deve ignorar categorias excluídas, de outro tipo e de outros usuários")
    void existsByNameAndIdNot_shouldIgnoreDeletedOtherTypeAndOtherUsers() {
        persist(owner, "Antiga", EXPENSE, true);
        persist(owner, "Outros", INCOME, false);
        persist(other, "De outro", EXPENSE, false);
        Category mine = persist(owner, "Mercado", EXPENSE, false);

        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalseAndIdNot(
                owner.getId(), "Antiga", EXPENSE, mine.getId())).isFalse();
        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalseAndIdNot(
                owner.getId(), "Outros", EXPENSE, mine.getId())).isFalse();
        assertThat(categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalseAndIdNot(
                owner.getId(), "De outro", EXPENSE, mine.getId())).isFalse();
    }

    // ---------- deleteAllByUserId ----------

    @Test
    @DisplayName("deleteAllByUserId: deve apagar todas as categorias do usuário e preservar as de outros")
    void deleteAllByUserId_shouldDeleteOnlyUserCategories() {
        persist(owner, "Mercado", EXPENSE, false);
        persist(owner, "Antiga", EXPENSE, true);
        persist(other, "De outro", EXPENSE, false);

        categoryRepository.deleteAllByUserId(owner.getId());
        categoryRepository.flush();
        entityManager.clear();

        assertThat(categoryRepository.findAllByUserIdAndDeletedFalse(owner.getId())).isEmpty();
        assertThat(categoryRepository.findByUserIdAndNameIgnoreCaseAndType(owner.getId(), "Antiga", EXPENSE)).isEmpty();
        assertThat(categoryRepository.findAllByUserIdAndDeletedFalse(other.getId()))
                .extracting(Category::getName).containsExactly("De outro");
    }
}