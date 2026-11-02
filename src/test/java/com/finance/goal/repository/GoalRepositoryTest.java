package com.finance.goal.repository;

import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.enums.GoalStatus;
import com.finance.common.enums.GoalType;
import com.finance.common.enums.TransactionType;
import com.finance.goal.entity.Goal;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GoalRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }

    @Autowired
    private GoalRepository goalRepository;

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

    private Goal persist(User user, String name, GoalType type, GoalStatus status) {
        Goal goal = new Goal();
        goal.setUser(user);
        goal.setType(type);
        goal.setName(name);
        goal.setTargetAmount(new BigDecimal("1000.00"));
        goal.setStatus(status);
        if (type == GoalType.SAVINGS) {
            goal.setCurrentAmount(BigDecimal.ZERO);
        }
        return goalRepository.saveAndFlush(goal);
    }

    private Category persistCategory(User user, String name) {
        return categoryRepository.saveAndFlush(new Category(user, name, TransactionType.EXPENSE, "#3B82F6", "tag"));
    }

    // ---------- persistência ----------

    @Test
    @DisplayName("save: deve persistir meta com id, auditoria e valores padrão")
    void save_shouldPersistGoalWithDefaults() {
        Goal goal = new Goal();
        goal.setUser(owner);
        goal.setType(GoalType.SAVINGS);
        goal.setName("Viagem");
        goal.setTargetAmount(new BigDecimal("5000.00"));
        goal = goalRepository.saveAndFlush(goal);
        entityManager.clear();

        Goal reloaded = goalRepository.findById(goal.getId()).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getName()).isEqualTo("Viagem");
        assertThat(reloaded.getType()).isEqualTo(GoalType.SAVINGS);
        assertThat(reloaded.getStatus()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(reloaded.getIcon()).isEqualTo("target");
        assertThat(reloaded.getTargetAmount()).isEqualByComparingTo("5000.00");
        assertThat(reloaded.getCurrentAmount()).isNull();
        assertThat(reloaded.getCategory()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save: deve preservar datas, categoria e valores monetários")
    void save_shouldKeepDatesCategoryAndAmounts() {
        Category category = persistCategory(owner, "Mercado");
        Goal goal = new Goal();
        goal.setUser(owner);
        goal.setType(GoalType.EXPENSE);
        goal.setName("Limite mercado");
        goal.setTargetAmount(new BigDecimal("800.50"));
        goal.setCategory(category);
        goal.setStartDate(LocalDate.of(2026, 1, 1));
        goal.setEndDate(LocalDate.of(2026, 1, 31));
        goal = goalRepository.saveAndFlush(goal);
        entityManager.clear();

        Goal reloaded = goalRepository.findById(goal.getId()).orElseThrow();

        assertThat(reloaded.getTargetAmount()).isEqualByComparingTo("800.50");
        assertThat(reloaded.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(reloaded.getEndDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(reloaded.getCategory().getId()).isEqualTo(category.getId());
    }

    @Test
    @DisplayName("save: deve falhar quando o nome (obrigatório) for nulo")
    void save_shouldFail_whenNameIsNull() {
        Goal invalid = new Goal();
        invalid.setUser(owner);
        invalid.setType(GoalType.SAVINGS);
        invalid.setTargetAmount(BigDecimal.TEN);

        assertThatThrownBy(() -> goalRepository.saveAndFlush(invalid)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("save: deve falhar quando o tipo (obrigatório) for nulo")
    void save_shouldFail_whenTypeIsNull() {
        Goal invalid = new Goal();
        invalid.setUser(owner);
        invalid.setName("Sem tipo");
        invalid.setTargetAmount(BigDecimal.TEN);

        assertThatThrownBy(() -> goalRepository.saveAndFlush(invalid)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("save: deve falhar quando o valor alvo (obrigatório) for nulo")
    void save_shouldFail_whenTargetAmountIsNull() {
        Goal invalid = new Goal();
        invalid.setUser(owner);
        invalid.setType(GoalType.SAVINGS);
        invalid.setName("Sem alvo");

        assertThatThrownBy(() -> goalRepository.saveAndFlush(invalid)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("save: deve falhar quando o usuário (obrigatório) for nulo")
    void save_shouldFail_whenUserIsNull() {
        Goal invalid = new Goal();
        invalid.setType(GoalType.SAVINGS);
        invalid.setName("Sem usuário");
        invalid.setTargetAmount(BigDecimal.TEN);

        assertThatThrownBy(() -> goalRepository.saveAndFlush(invalid)).isInstanceOf(Exception.class);
    }

    // ---------- findByIdAndUserId ----------

    @Test
    @DisplayName("findByIdAndUserId: deve retornar a meta do usuário, inclusive cancelada")
    void findByIdAndUserId_shouldReturnGoal_whenOwned() {
        Goal active = persist(owner, "Viagem", GoalType.SAVINGS, GoalStatus.ACTIVE);
        Goal cancelled = persist(owner, "Antiga", GoalType.SAVINGS, GoalStatus.CANCELLED);

        assertThat(goalRepository.findByIdAndUserId(active.getId(), owner.getId())).isPresent();
        assertThat(goalRepository.findByIdAndUserId(cancelled.getId(), owner.getId())).isPresent();
    }

    @Test
    @DisplayName("findByIdAndUserId: deve retornar vazio quando a meta é de outro usuário")
    void findByIdAndUserId_shouldReturnEmpty_whenOwnedByAnotherUser() {
        Goal goal = persist(owner, "Viagem", GoalType.SAVINGS, GoalStatus.ACTIVE);

        assertThat(goalRepository.findByIdAndUserId(goal.getId(), other.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId: deve retornar vazio quando a meta não existe")
    void findByIdAndUserId_shouldReturnEmpty_whenNotExists() {
        assertThat(goalRepository.findByIdAndUserId(999_999L, owner.getId())).isEmpty();
    }

    // ---------- findAllByUserId ----------

    @Test
    @DisplayName("findAllByUserId: deve retornar todas as metas do usuário e ignorar as de outros")
    void findAllByUserId_shouldReturnOnlyUserGoals() {
        persist(owner, "Viagem", GoalType.SAVINGS, GoalStatus.ACTIVE);
        persist(owner, "Antiga", GoalType.EXPENSE, GoalStatus.CANCELLED);
        persist(other, "De outro", GoalType.SAVINGS, GoalStatus.ACTIVE);

        List<Goal> result = goalRepository.findAllByUserId(owner.getId());

        assertThat(result).extracting(Goal::getName).containsExactlyInAnyOrder("Viagem", "Antiga");
    }

    @Test
    @DisplayName("findAllByUserId: deve retornar lista vazia quando o usuário não tem metas")
    void findAllByUserId_shouldReturnEmpty_whenUserHasNoGoals() {
        assertThat(goalRepository.findAllByUserId(owner.getId())).isEmpty();
    }

    // ---------- findAllByUserIdAndStatus ----------

    @Test
    @DisplayName("findAllByUserIdAndStatus: deve filtrar pelo status e ignorar metas de outros usuários")
    void findAllByUserIdAndStatus_shouldFilterByStatus() {
        persist(owner, "Ativa", GoalType.SAVINGS, GoalStatus.ACTIVE);
        persist(owner, "Cancelada", GoalType.SAVINGS, GoalStatus.CANCELLED);
        persist(other, "De outro", GoalType.SAVINGS, GoalStatus.ACTIVE);

        assertThat(goalRepository.findAllByUserIdAndStatus(owner.getId(), GoalStatus.ACTIVE))
                .extracting(Goal::getName).containsExactly("Ativa");
        assertThat(goalRepository.findAllByUserIdAndStatus(owner.getId(), GoalStatus.CANCELLED))
                .extracting(Goal::getName).containsExactly("Cancelada");
    }

    // ---------- existsByIdAndUserId ----------

    @Test
    @DisplayName("existsByIdAndUserId: deve retornar true somente para o dono")
    void existsByIdAndUserId_shouldReturnTrueOnlyForOwner() {
        Goal goal = persist(owner, "Viagem", GoalType.SAVINGS, GoalStatus.ACTIVE);

        assertThat(goalRepository.existsByIdAndUserId(goal.getId(), owner.getId())).isTrue();
        assertThat(goalRepository.existsByIdAndUserId(goal.getId(), other.getId())).isFalse();
        assertThat(goalRepository.existsByIdAndUserId(999_999L, owner.getId())).isFalse();
    }

    // ---------- existsByCategoryId ----------

    @Test
    @DisplayName("existsByCategoryId: deve retornar true quando existe meta vinculada à categoria")
    void existsByCategoryId_shouldReturnTrue_whenGoalUsesCategory() {
        Category category = persistCategory(owner, "Mercado");
        Goal goal = persist(owner, "Limite", GoalType.EXPENSE, GoalStatus.ACTIVE);
        goal.setCategory(category);
        goalRepository.saveAndFlush(goal);

        assertThat(goalRepository.existsByCategoryId(category.getId())).isTrue();
    }

    @Test
    @DisplayName("existsByCategoryId: deve retornar false quando nenhuma meta usa a categoria")
    void existsByCategoryId_shouldReturnFalse_whenNoGoalUsesCategory() {
        Category category = persistCategory(owner, "Mercado");
        persist(owner, "Sem categoria", GoalType.SAVINGS, GoalStatus.ACTIVE);

        assertThat(goalRepository.existsByCategoryId(category.getId())).isFalse();
        assertThat(goalRepository.existsByCategoryId(999_999L)).isFalse();
    }

    // ---------- deleteAllByUserId ----------

    @Test
    @DisplayName("deleteAllByUserId: deve apagar todas as metas do usuário e preservar as de outros")
    void deleteAllByUserId_shouldDeleteOnlyUserGoals() {
        persist(owner, "Viagem", GoalType.SAVINGS, GoalStatus.ACTIVE);
        persist(owner, "Antiga", GoalType.EXPENSE, GoalStatus.CANCELLED);
        persist(other, "De outro", GoalType.SAVINGS, GoalStatus.ACTIVE);

        goalRepository.deleteAllByUserId(owner.getId());
        goalRepository.flush();
        entityManager.clear();

        assertThat(goalRepository.findAllByUserId(owner.getId())).isEmpty();
        assertThat(goalRepository.findAllByUserId(other.getId()))
                .extracting(Goal::getName).containsExactly("De outro");
    }
}