package com.finance.goal.repository;

import com.finance.common.enums.GoalType;
import com.finance.goal.entity.Goal;
import com.finance.goal.entity.GoalContribution;
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
class GoalContributionRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }

    @Autowired
    private GoalContributionRepository contributionRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User other;
    private Goal ownerGoal;
    private Goal otherGoal;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(new User("google-owner", "owner@example.com", "Owner", null));
        other = userRepository.saveAndFlush(new User("google-other", "other@example.com", "Other", null));
        ownerGoal = persistGoal(owner, "Viagem");
        otherGoal = persistGoal(other, "Carro");
    }

    private Goal persistGoal(User user, String name) {
        Goal goal = new Goal();
        goal.setUser(user);
        goal.setType(GoalType.SAVINGS);
        goal.setName(name);
        goal.setTargetAmount(new BigDecimal("1000.00"));
        goal.setCurrentAmount(BigDecimal.ZERO);
        return goalRepository.saveAndFlush(goal);
    }

    private GoalContribution persist(Goal goal, String amount, LocalDate date, String notes) {
        return contributionRepository.saveAndFlush(
                new GoalContribution(goal, new BigDecimal(amount), date, notes));
    }

    // ---------- persistência ----------

    @Test
    @DisplayName("save: deve persistir contribuição com id, auditoria e valores")
    void save_shouldPersistContribution() {
        GoalContribution contribution = persist(ownerGoal, "150.75", LocalDate.of(2026, 3, 10), "Bônus");
        entityManager.clear();

        GoalContribution reloaded = contributionRepository.findById(contribution.getId()).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getAmount()).isEqualByComparingTo("150.75");
        assertThat(reloaded.getDate()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(reloaded.getNotes()).isEqualTo("Bônus");
        assertThat(reloaded.getGoal().getId()).isEqualTo(ownerGoal.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save: deve aceitar valor negativo (retirada) e observação nula")
    void save_shouldAcceptNegativeAmountAndNullNotes() {
        GoalContribution contribution = persist(ownerGoal, "-50.00", LocalDate.of(2026, 3, 11), null);
        entityManager.clear();

        GoalContribution reloaded = contributionRepository.findById(contribution.getId()).orElseThrow();

        assertThat(reloaded.getAmount()).isEqualByComparingTo("-50.00");
        assertThat(reloaded.getNotes()).isNull();
    }

    @Test
    @DisplayName("save: deve falhar quando o valor (obrigatório) for nulo")
    void save_shouldFail_whenAmountIsNull() {
        GoalContribution invalid = new GoalContribution(ownerGoal, null, LocalDate.of(2026, 3, 10), null);

        assertThatThrownBy(() -> contributionRepository.saveAndFlush(invalid)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("save: deve falhar quando a data (obrigatória) for nula")
    void save_shouldFail_whenDateIsNull() {
        GoalContribution invalid = new GoalContribution(ownerGoal, BigDecimal.TEN, null, null);

        assertThatThrownBy(() -> contributionRepository.saveAndFlush(invalid)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("save: deve falhar quando a meta (obrigatória) for nula")
    void save_shouldFail_whenGoalIsNull() {
        GoalContribution invalid = new GoalContribution(null, BigDecimal.TEN, LocalDate.of(2026, 3, 10), null);

        assertThatThrownBy(() -> contributionRepository.saveAndFlush(invalid)).isInstanceOf(Exception.class);
    }

    // ---------- findByIdAndGoalUserId ----------

    @Test
    @DisplayName("findByIdAndGoalUserId: deve retornar a contribuição quando a meta pertence ao usuário")
    void findByIdAndGoalUserId_shouldReturnContribution_whenOwned() {
        GoalContribution contribution = persist(ownerGoal, "100.00", LocalDate.of(2026, 3, 10), null);

        assertThat(contributionRepository.findByIdAndGoalUserId(contribution.getId(), owner.getId())).isPresent();
    }

    @Test
    @DisplayName("findByIdAndGoalUserId: deve retornar vazio quando a meta é de outro usuário")
    void findByIdAndGoalUserId_shouldReturnEmpty_whenOwnedByAnotherUser() {
        GoalContribution contribution = persist(ownerGoal, "100.00", LocalDate.of(2026, 3, 10), null);

        assertThat(contributionRepository.findByIdAndGoalUserId(contribution.getId(), other.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndGoalUserId: deve retornar vazio quando a contribuição não existe")
    void findByIdAndGoalUserId_shouldReturnEmpty_whenNotExists() {
        assertThat(contributionRepository.findByIdAndGoalUserId(999_999L, owner.getId())).isEmpty();
    }

    // ---------- findAllByGoalIdAndGoalUserIdOrderByDateDesc ----------

    @Test
    @DisplayName("findAllByGoalIdAndGoalUserIdOrderByDateDesc: deve retornar da data mais recente para a mais antiga")
    void findAll_shouldReturnOrderedByDateDesc() {
        persist(ownerGoal, "10.00", LocalDate.of(2026, 1, 5), "antiga");
        persist(ownerGoal, "20.00", LocalDate.of(2026, 3, 5), "recente");
        persist(ownerGoal, "30.00", LocalDate.of(2026, 2, 5), "meio");

        List<GoalContribution> result = contributionRepository
                .findAllByGoalIdAndGoalUserIdOrderByDateDesc(ownerGoal.getId(), owner.getId());

        assertThat(result).extracting(GoalContribution::getNotes)
                .containsExactly("recente", "meio", "antiga");
    }

    @Test
    @DisplayName("findAllByGoalIdAndGoalUserIdOrderByDateDesc: deve ignorar outras metas e outros usuários")
    void findAll_shouldIgnoreOtherGoalsAndUsers() {
        Goal secondGoal = persistGoal(owner, "Reserva");
        persist(ownerGoal, "10.00", LocalDate.of(2026, 1, 5), "minha");
        persist(secondGoal, "20.00", LocalDate.of(2026, 1, 6), "outra meta");
        persist(otherGoal, "30.00", LocalDate.of(2026, 1, 7), "de outro");

        assertThat(contributionRepository.findAllByGoalIdAndGoalUserIdOrderByDateDesc(ownerGoal.getId(), owner.getId()))
                .extracting(GoalContribution::getNotes).containsExactly("minha");
        assertThat(contributionRepository.findAllByGoalIdAndGoalUserIdOrderByDateDesc(otherGoal.getId(), owner.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("findAllByGoalIdAndGoalUserIdOrderByDateDesc: deve retornar lista vazia quando não há contribuições")
    void findAll_shouldReturnEmpty_whenNoContributions() {
        assertThat(contributionRepository.findAllByGoalIdAndGoalUserIdOrderByDateDesc(ownerGoal.getId(), owner.getId()))
                .isEmpty();
    }

    // ---------- existsByIdAndGoalUserId ----------

    @Test
    @DisplayName("existsByIdAndGoalUserId: deve retornar true somente para o dono da meta")
    void existsByIdAndGoalUserId_shouldReturnTrueOnlyForOwner() {
        GoalContribution contribution = persist(ownerGoal, "100.00", LocalDate.of(2026, 3, 10), null);

        assertThat(contributionRepository.existsByIdAndGoalUserId(contribution.getId(), owner.getId())).isTrue();
        assertThat(contributionRepository.existsByIdAndGoalUserId(contribution.getId(), other.getId())).isFalse();
        assertThat(contributionRepository.existsByIdAndGoalUserId(999_999L, owner.getId())).isFalse();
    }

    // ---------- deleteAllByGoalUserId ----------

    @Test
    @DisplayName("deleteAllByGoalUserId: deve apagar as contribuições do usuário e preservar as de outros")
    void deleteAllByGoalUserId_shouldDeleteOnlyUserContributions() {
        persist(ownerGoal, "10.00", LocalDate.of(2026, 1, 5), "minha 1");
        persist(ownerGoal, "20.00", LocalDate.of(2026, 1, 6), "minha 2");
        persist(otherGoal, "30.00", LocalDate.of(2026, 1, 7), "de outro");

        contributionRepository.deleteAllByGoalUserId(owner.getId());
        contributionRepository.flush();
        entityManager.clear();

        assertThat(contributionRepository.findAllByGoalIdAndGoalUserIdOrderByDateDesc(ownerGoal.getId(), owner.getId()))
                .isEmpty();
        assertThat(contributionRepository.findAllByGoalIdAndGoalUserIdOrderByDateDesc(otherGoal.getId(), other.getId()))
                .extracting(GoalContribution::getNotes).containsExactly("de outro");
    }
}