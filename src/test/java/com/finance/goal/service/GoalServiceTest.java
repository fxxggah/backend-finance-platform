package com.finance.goal.service;

import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.enums.GoalState;
import com.finance.common.enums.GoalStatus;
import com.finance.common.enums.GoalType;
import com.finance.common.enums.TransactionType;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.goal.dto.request.GoalContributionRequest;
import com.finance.goal.dto.request.GoalRequest;
import com.finance.goal.dto.response.GoalContributionResponse;
import com.finance.goal.dto.response.GoalResponse;
import com.finance.goal.entity.Goal;
import com.finance.goal.entity.GoalContribution;
import com.finance.goal.repository.GoalContributionRepository;
import com.finance.goal.repository.GoalRepository;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long GOAL_ID = 10L;
    private static final Long CONTRIBUTION_ID = 100L;
    private static final Long CATEGORY_ID = 5L;

    private static final String GOAL_NOT_FOUND = "Meta não encontrada";
    private static final String CONTRIBUTION_NOT_FOUND = "Contribuição não encontrada";
    private static final String CATEGORY_NOT_FOUND = "Categoria não encontrada";

    @Mock
    private GoalRepository goalRepository;
    @Mock
    private GoalContributionRepository contributionRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GoalService goalService;

    private User user;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        lenient().when(user.getId()).thenReturn(USER_ID);
    }

    // ---------- helpers ----------

    private User user() {
        return user;
    }

    private Goal goal(Long id, GoalType type, String target, String current) {
        Goal goal = new Goal();
        goal.setId(id);
        goal.setUser(user());
        goal.setType(type);
        goal.setName("Meta");
        goal.setTargetAmount(new BigDecimal(target));
        goal.setCurrentAmount(current == null ? null : new BigDecimal(current));
        return goal;
    }

    private Category category(TransactionType type) {
        Category category = new Category(user(), "Mercado", type, "#3B82F6", "tag");
        category.setId(CATEGORY_ID);
        return category;
    }

    private GoalRequest request(GoalType type, String target, LocalDate start, LocalDate end, Long categoryId) {
        return new GoalRequest(type, "Viagem", "Descrição", null, "#3B82F6",
                new BigDecimal(target), categoryId, start, end);
    }

    private void stubFindOwned(Goal goal) {
        when(goalRepository.findByIdAndUserId(goal.getId(), USER_ID)).thenReturn(Optional.of(goal));
    }

    private void stubSaveReturnsArgument() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private GoalResponse getResponse(Goal goal) {
        stubFindOwned(goal);
        return goalService.getGoal(goal.getId(), USER_ID);
    }

    // ---------- listGoals ----------

    @Test
    @DisplayName("listGoals: sem filtros deve retornar todas as metas do usuário")
    void listGoals_withoutFilters_shouldReturnAll() {
        when(goalRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                goal(1L, GoalType.SAVINGS, "1000.00", "0"),
                goal(2L, GoalType.EXPENSE, "500.00", null)));

        List<GoalResponse> response = goalService.listGoals(USER_ID, null, null);

        assertThat(response).hasSize(2);
        assertThat(response).extracting(GoalResponse::type).containsExactly(GoalType.SAVINGS, GoalType.EXPENSE);
        verify(goalRepository, never()).findAllByUserIdAndStatus(anyLong(), any());
    }

    @Test
    @DisplayName("listGoals: com status deve usar a query filtrada por status")
    void listGoals_withStatus_shouldQueryByStatus() {
        when(goalRepository.findAllByUserIdAndStatus(USER_ID, GoalStatus.ACTIVE))
                .thenReturn(List.of(goal(1L, GoalType.SAVINGS, "1000.00", "0")));

        List<GoalResponse> response = goalService.listGoals(USER_ID, null, GoalStatus.ACTIVE);

        assertThat(response).hasSize(1);
        verify(goalRepository, never()).findAllByUserId(anyLong());
    }

    @Test
    @DisplayName("listGoals: com tipo deve filtrar em memória pelo tipo")
    void listGoals_withType_shouldFilterByType() {
        when(goalRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                goal(1L, GoalType.SAVINGS, "1000.00", "0"),
                goal(2L, GoalType.EXPENSE, "500.00", null),
                goal(3L, GoalType.INCOME, "700.00", null)));

        List<GoalResponse> response = goalService.listGoals(USER_ID, GoalType.EXPENSE, null);

        assertThat(response).extracting(GoalResponse::id).containsExactly(2L);
    }

    @Test
    @DisplayName("listGoals: com tipo e status deve combinar os dois filtros")
    void listGoals_withTypeAndStatus_shouldCombineFilters() {
        when(goalRepository.findAllByUserIdAndStatus(USER_ID, GoalStatus.CANCELLED)).thenReturn(List.of(
                goal(1L, GoalType.SAVINGS, "1000.00", "0"),
                goal(2L, GoalType.EXPENSE, "500.00", null)));

        List<GoalResponse> response = goalService.listGoals(USER_ID, GoalType.SAVINGS, GoalStatus.CANCELLED);

        assertThat(response).extracting(GoalResponse::id).containsExactly(1L);
    }

    @Test
    @DisplayName("listGoals: deve retornar lista vazia quando o usuário não tem metas")
    void listGoals_shouldReturnEmpty_whenUserHasNoGoals() {
        when(goalRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        assertThat(goalService.listGoals(USER_ID, null, null)).isEmpty();
    }

    // ---------- getGoal ----------

    @Test
    @DisplayName("getGoal: deve retornar a meta com percentual calculado")
    void getGoal_shouldReturnGoalWithPercentage() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "250.00");

        GoalResponse response = getResponse(goal);

        assertThat(response.id()).isEqualTo(GOAL_ID);
        assertThat(response.name()).isEqualTo("Meta");
        assertThat(response.targetAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.currentAmount()).isEqualByComparingTo("250.00");
        assertThat(response.percentage()).isEqualByComparingTo("25.00");
        assertThat(response.status()).isEqualTo(GoalStatus.ACTIVE);
    }

    @Test
    @DisplayName("getGoal: deve tratar currentAmount nulo como zero")
    void getGoal_shouldTreatNullCurrentAmountAsZero() {
        GoalResponse response = getResponse(goal(GOAL_ID, GoalType.EXPENSE, "500.00", null));

        assertThat(response.currentAmount()).isEqualByComparingTo("0");
        assertThat(response.percentage()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("getGoal: deve arredondar o percentual para 2 casas (HALF_UP)")
    void getGoal_shouldRoundPercentage() {
        GoalResponse response = getResponse(goal(GOAL_ID, GoalType.SAVINGS, "3.00", "1.00"));

        assertThat(response.percentage()).isEqualByComparingTo("33.33");
    }

    @Test
    @DisplayName("getGoal: deve incluir a categoria quando a meta possui uma")
    void getGoal_shouldIncludeCategory() {
        Goal goal = goal(GOAL_ID, GoalType.EXPENSE, "500.00", null);
        goal.setCategory(category(TransactionType.EXPENSE));

        GoalResponse response = getResponse(goal);

        assertThat(response.category()).isNotNull();
        assertThat(response.category().id()).isEqualTo(CATEGORY_ID);
        assertThat(response.category().name()).isEqualTo("Mercado");
        assertThat(response.category().color()).isEqualTo("#3B82F6");
        assertThat(response.category().icon()).isEqualTo("tag");
    }

    @Test
    @DisplayName("getGoal: categoria deve ser nula quando a meta não possui categoria")
    void getGoal_shouldReturnNullCategory_whenGoalHasNone() {
        assertThat(getResponse(goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "0")).category()).isNull();
    }

    @Test
    @DisplayName("getGoal: deve lançar ResourceNotFoundException quando a meta não existe ou é de outro usuário")
    void getGoal_shouldThrowNotFound() {
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.getGoal(GOAL_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(GOAL_NOT_FOUND);
    }

    // ---------- state (EXPENSE) ----------

    @Test
    @DisplayName("state EXPENSE: abaixo de 80% e período em andamento deve ser IN_PROGRESS")
    void state_expense_shouldBeInProgress_whenBelowWarning() {
        Goal goal = goal(GOAL_ID, GoalType.EXPENSE, "1000.00", "500.00");

        assertThat(getResponse(goal).state()).isEqualTo(GoalState.IN_PROGRESS);
    }

    @Test
    @DisplayName("state EXPENSE: a partir de 80% (até 100%) deve ser WARNING")
    void state_expense_shouldBeWarning_whenAtOrAbove80Percent() {
        Goal goal = goal(GOAL_ID, GoalType.EXPENSE, "1000.00", "800.00");
        assertThat(getResponse(goal).state()).isEqualTo(GoalState.WARNING);
    }

    @Test
    @DisplayName("state EXPENSE: exatamente 100% com período em andamento ainda é WARNING")
    void state_expense_shouldBeWarning_whenExactly100Percent() {
        Goal goal = goal(GOAL_ID, GoalType.EXPENSE, "1000.00", "1000.00");

        assertThat(getResponse(goal).state()).isEqualTo(GoalState.WARNING);
    }

    @Test
    @DisplayName("state EXPENSE: acima de 100% deve ser EXCEEDED")
    void state_expense_shouldBeExceeded_whenAbove100Percent() {
        Goal goal = goal(GOAL_ID, GoalType.EXPENSE, "1000.00", "1200.00");

        assertThat(getResponse(goal).state()).isEqualTo(GoalState.EXCEEDED);
    }

    @Test
    @DisplayName("state EXPENSE: período encerrado sem estourar o limite deve ser ACHIEVED")
    void state_expense_shouldBeAchieved_whenPeriodEndedWithinLimit() {
        Goal goal = goal(GOAL_ID, GoalType.EXPENSE, "1000.00", "500.00");
        goal.setEndDate(LocalDate.now().minusDays(1));

        assertThat(getResponse(goal).state()).isEqualTo(GoalState.ACHIEVED);
    }

    @Test
    @DisplayName("state EXPENSE: período encerrado estourando o limite deve continuar EXCEEDED")
    void state_expense_shouldBeExceeded_whenPeriodEndedOverLimit() {
        Goal goal = goal(GOAL_ID, GoalType.EXPENSE, "1000.00", "1500.00");
        goal.setEndDate(LocalDate.now().minusDays(1));

        assertThat(getResponse(goal).state()).isEqualTo(GoalState.EXCEEDED);
    }

    // ---------- state (SAVINGS / INCOME) ----------

    @Test
    @DisplayName("state SAVINGS: 100% ou mais deve ser ACHIEVED")
    void state_savings_shouldBeAchieved_whenTargetReached() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "1000.00");

        assertThat(getResponse(goal).state()).isEqualTo(GoalState.ACHIEVED);
    }

    @Test
    @DisplayName("state SAVINGS: período encerrado sem atingir o alvo deve ser EXPIRED")
    void state_savings_shouldBeExpired_whenPeriodEndedBelowTarget() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "500.00");
        goal.setEndDate(LocalDate.now().minusDays(1));

        assertThat(getResponse(goal).state()).isEqualTo(GoalState.EXPIRED);
    }

    @Test
    @DisplayName("state SAVINGS: período encerrado mas alvo atingido deve ser ACHIEVED")
    void state_savings_shouldBeAchieved_whenPeriodEndedButTargetReached() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "1000.00");
        goal.setEndDate(LocalDate.now().minusDays(1));

        assertThat(getResponse(goal).state()).isEqualTo(GoalState.ACHIEVED);
    }

    @Test
    @DisplayName("state SAVINGS: abaixo do alvo e dentro do prazo deve ser IN_PROGRESS")
    void state_savings_shouldBeInProgress_whenBelowTargetAndWithinPeriod() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "500.00");
        goal.setEndDate(LocalDate.now().plusMonths(2));

        assertThat(getResponse(goal).state()).isEqualTo(GoalState.IN_PROGRESS);
    }

    @Test
    @DisplayName("state INCOME: sem progresso e dentro do prazo deve ser IN_PROGRESS")
    void state_income_shouldBeInProgress_whenNoProgress() {
        Goal goal = goal(GOAL_ID, GoalType.INCOME, "1000.00", null);
        goal.setEndDate(LocalDate.now().plusMonths(1));

        assertThat(getResponse(goal).state()).isEqualTo(GoalState.IN_PROGRESS);
    }

    // ---------- monthlyNeeded ----------

    @Test
    @DisplayName("monthlyNeeded: deve dividir o valor restante pelos meses até a data final")
    void monthlyNeeded_shouldDivideRemainingByMonths() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "200.00");
        goal.setEndDate(LocalDate.now().plusMonths(4));

        assertThat(getResponse(goal).monthlyNeeded()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("monthlyNeeded: deve considerar no mínimo 1 mês quando a data final já passou ou é este mês")
    void monthlyNeeded_shouldUseAtLeastOneMonth() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "200.00");
        goal.setEndDate(LocalDate.now().minusMonths(2));

        assertThat(getResponse(goal).monthlyNeeded()).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("monthlyNeeded: deve ser zero quando o alvo já foi atingido")
    void monthlyNeeded_shouldBeZero_whenTargetReached() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "1200.00");
        goal.setEndDate(LocalDate.now().plusMonths(3));

        assertThat(getResponse(goal).monthlyNeeded()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("monthlyNeeded: deve ser nulo quando a meta SAVINGS não tem data final")
    void monthlyNeeded_shouldBeNull_whenNoEndDate() {
        assertThat(getResponse(goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "0")).monthlyNeeded()).isNull();
    }

    @Test
    @DisplayName("monthlyNeeded: deve ser nulo para metas que não são SAVINGS")
    void monthlyNeeded_shouldBeNull_whenNotSavings() {
        Goal goal = goal(GOAL_ID, GoalType.EXPENSE, "1000.00", null);
        goal.setEndDate(LocalDate.now().plusMonths(3));

        assertThat(getResponse(goal).monthlyNeeded()).isNull();
    }

    // ---------- createGoal ----------

    @Test
    @DisplayName("createGoal: meta SAVINGS deve iniciar com saldo zero e ícone padrão")
    void createGoal_savings_shouldStartWithZeroAndDefaultIcon() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        stubSaveReturnsArgument();

        GoalResponse response = goalService.createGoal(USER_ID,
                request(GoalType.SAVINGS, "5000.00", null, null, null));

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        Goal persisted = captor.getValue();
        assertThat(persisted.getType()).isEqualTo(GoalType.SAVINGS);
        assertThat(persisted.getName()).isEqualTo("Viagem");
        assertThat(persisted.getCurrentAmount()).isEqualByComparingTo("0");
        assertThat(persisted.getIcon()).isEqualTo("target");
        assertThat(persisted.getStatus()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(persisted.getCategory()).isNull();

        assertThat(response.targetAmount()).isEqualByComparingTo("5000.00");
        assertThat(response.currentAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("createGoal: deve manter o ícone informado")
    void createGoal_shouldKeepProvidedIcon() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        stubSaveReturnsArgument();
        GoalRequest request = new GoalRequest(GoalType.SAVINGS, "Viagem", null, "plane", null,
                new BigDecimal("100.00"), null, null, null);

        goalService.createGoal(USER_ID, request);

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        assertThat(captor.getValue().getIcon()).isEqualTo("plane");
    }

    @Test
    @DisplayName("createGoal: meta EXPENSE não deve definir saldo e deve vincular a categoria compatível")
    void createGoal_expense_shouldLinkCategory() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(TransactionType.EXPENSE)));
        stubSaveReturnsArgument();

        goalService.createGoal(USER_ID, request(GoalType.EXPENSE, "800.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), CATEGORY_ID));

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        Goal persisted = captor.getValue();
        assertThat(persisted.getCurrentAmount()).isNull();
        assertThat(persisted.getCategory().getId()).isEqualTo(CATEGORY_ID);
        assertThat(persisted.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(persisted.getEndDate()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    @DisplayName("createGoal: deve lançar ResourceNotFoundException quando o usuário não existe")
    void createGoal_shouldThrowNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.createGoal(USER_ID,
                request(GoalType.SAVINGS, "1000.00", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");

        verifyNoInteractions(goalRepository);
    }

    @Test
    @DisplayName("createGoal: metas INCOME/EXPENSE sem datas devem lançar BusinessRuleException")
    void createGoal_shouldThrowBusinessRule_whenIncomeOrExpenseWithoutDates() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> goalService.createGoal(USER_ID,
                request(GoalType.EXPENSE, "1000.00", null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Metas de INCOME/EXPENSE exigem startDate e endDate");

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("createGoal: meta INCOME com apenas uma das datas deve lançar BusinessRuleException")
    void createGoal_shouldThrowBusinessRule_whenOnlyOneDateInformed() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> goalService.createGoal(USER_ID,
                request(GoalType.INCOME, "1000.00", LocalDate.of(2026, 1, 1), null, null)))
                .isInstanceOf(BusinessRuleException.class);

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("createGoal: data final anterior à inicial deve lançar BusinessRuleException")
    void createGoal_shouldThrowBusinessRule_whenEndDateBeforeStartDate() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> goalService.createGoal(USER_ID,
                request(GoalType.INCOME, "1000.00", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A data final não pode ser anterior à data inicial");

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("createGoal: meta SAVINGS não exige datas")
    void createGoal_savings_shouldNotRequireDates() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        stubSaveReturnsArgument();

        GoalResponse response = goalService.createGoal(USER_ID,
                request(GoalType.SAVINGS, "1000.00", null, null, null));

        assertThat(response.startDate()).isNull();
        assertThat(response.endDate()).isNull();
    }

    @Test
    @DisplayName("createGoal: categoria inexistente ou de outro usuário deve lançar ResourceNotFoundException")
    void createGoal_shouldThrowNotFound_whenCategoryNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.createGoal(USER_ID, request(GoalType.EXPENSE, "800.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), CATEGORY_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(CATEGORY_NOT_FOUND);

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("createGoal: meta INCOME com categoria de despesa deve lançar BusinessRuleException")
    void createGoal_shouldThrowBusinessRule_whenIncomeGoalWithExpenseCategory() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(TransactionType.EXPENSE)));

        assertThatThrownBy(() -> goalService.createGoal(USER_ID, request(GoalType.INCOME, "800.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), CATEGORY_ID)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Meta de receita exige uma categoria de receita");

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("createGoal: meta EXPENSE com categoria de receita deve lançar BusinessRuleException")
    void createGoal_shouldThrowBusinessRule_whenExpenseGoalWithIncomeCategory() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(TransactionType.INCOME)));

        assertThatThrownBy(() -> goalService.createGoal(USER_ID, request(GoalType.EXPENSE, "800.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), CATEGORY_ID)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Meta de despesa exige uma categoria de despesa");

        verify(goalRepository, never()).save(any(Goal.class));
    }

    // ---------- updateGoal ----------

    @Test
    @DisplayName("updateGoal: deve atualizar os campos da meta existente")
    void updateGoal_shouldUpdateFields() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "300.00");
        stubFindOwned(goal);
        stubSaveReturnsArgument();
        GoalRequest request = new GoalRequest(GoalType.SAVINGS, "Novo nome", "Nova desc", "car", "#000000",
                new BigDecimal("2000.00"), null, null, LocalDate.now().plusMonths(5));

        GoalResponse response = goalService.updateGoal(GOAL_ID, USER_ID, request);

        assertThat(goal.getName()).isEqualTo("Novo nome");
        assertThat(goal.getDescription()).isEqualTo("Nova desc");
        assertThat(goal.getIcon()).isEqualTo("car");
        assertThat(goal.getColor()).isEqualTo("#000000");
        assertThat(goal.getTargetAmount()).isEqualByComparingTo("2000.00");
        assertThat(goal.getCurrentAmount()).isEqualByComparingTo("300.00");
        assertThat(response.name()).isEqualTo("Novo nome");
        assertThat(response.percentage()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("updateGoal: deve remover a categoria quando categoryId vier nulo")
    void updateGoal_shouldClearCategory_whenCategoryIdIsNull() {
        Goal goal = goal(GOAL_ID, GoalType.EXPENSE, "1000.00", null);
        goal.setCategory(category(TransactionType.EXPENSE));
        stubFindOwned(goal);
        stubSaveReturnsArgument();

        goalService.updateGoal(GOAL_ID, USER_ID, request(GoalType.EXPENSE, "1000.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null));

        assertThat(goal.getCategory()).isNull();
    }

    @Test
    @DisplayName("updateGoal: deve lançar ResourceNotFoundException quando a meta não existe")
    void updateGoal_shouldThrowNotFound() {
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.updateGoal(GOAL_ID, USER_ID,
                request(GoalType.SAVINGS, "1000.00", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(GOAL_NOT_FOUND);

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("updateGoal: deve validar as regras de datas e não alterar a meta")
    void updateGoal_shouldThrowBusinessRule_whenDatesInvalid() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "0");
        stubFindOwned(goal);

        assertThatThrownBy(() -> goalService.updateGoal(GOAL_ID, USER_ID,
                request(GoalType.EXPENSE, "500.00", null, null, null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(goal.getType()).isEqualTo(GoalType.SAVINGS);
        verify(goalRepository, never()).save(any(Goal.class));
    }

    // ---------- cancelGoal ----------

    @Test
    @DisplayName("cancelGoal: deve marcar a meta como CANCELLED")
    void cancelGoal_shouldSetStatusCancelled() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "0");
        stubFindOwned(goal);

        goalService.cancelGoal(GOAL_ID, USER_ID);

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.CANCELLED);
        verify(goalRepository).save(goal);
    }

    @Test
    @DisplayName("cancelGoal: deve lançar ResourceNotFoundException quando a meta não existe")
    void cancelGoal_shouldThrowNotFound() {
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.cancelGoal(GOAL_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(GOAL_NOT_FOUND);

        verify(goalRepository, never()).save(any(Goal.class));
    }

    // ---------- deleteGoal ----------

    @Test
    @DisplayName("deleteGoal: deve excluir a meta do usuário")
    void deleteGoal_shouldDelete() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "0");
        stubFindOwned(goal);

        goalService.deleteGoal(GOAL_ID, USER_ID);

        verify(goalRepository).delete(goal);
    }

    @Test
    @DisplayName("deleteGoal: deve lançar ResourceNotFoundException quando a meta não existe")
    void deleteGoal_shouldThrowNotFound() {
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.deleteGoal(GOAL_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(GOAL_NOT_FOUND);

        verify(goalRepository, never()).delete(any(Goal.class));
    }

    // ---------- listContributions ----------

    @Test
    @DisplayName("listContributions: deve mapear as contribuições da meta")
    void listContributions_shouldReturnMappedContributions() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "150.00");
        GoalContribution first = new GoalContribution(goal, new BigDecimal("100.00"), LocalDate.of(2026, 3, 2), "A");
        first.setId(1L);
        GoalContribution second = new GoalContribution(goal, new BigDecimal("50.00"), LocalDate.of(2026, 3, 1), null);
        second.setId(2L);
        stubFindOwned(goal);
        when(contributionRepository.findAllByGoalIdAndGoalUserIdOrderByDateDesc(GOAL_ID, USER_ID))
                .thenReturn(List.of(first, second));

        List<GoalContributionResponse> response = goalService.listContributions(GOAL_ID, USER_ID);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).id()).isEqualTo(1L);
        assertThat(response.get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(response.get(0).date()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(response.get(0).notes()).isEqualTo("A");
        assertThat(response.get(1).notes()).isNull();
    }

    @Test
    @DisplayName("listContributions: deve lançar ResourceNotFoundException quando a meta não pertence ao usuário")
    void listContributions_shouldThrowNotFound_whenGoalNotOwned() {
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.listContributions(GOAL_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(GOAL_NOT_FOUND);

        verifyNoInteractions(contributionRepository);
    }

    // ---------- addContribution ----------

    @Test
    @DisplayName("addContribution: depósito deve somar ao saldo e registrar a contribuição")
    void addContribution_deposit_shouldIncreaseBalance() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "100.00");
        stubFindOwned(goal);
        stubSaveReturnsArgument();
        GoalContributionRequest request = new GoalContributionRequest(
                new BigDecimal("50.00"), LocalDate.of(2026, 3, 10), "Bônus");

        GoalResponse response = goalService.addContribution(GOAL_ID, USER_ID, request);

        ArgumentCaptor<GoalContribution> captor = ArgumentCaptor.forClass(GoalContribution.class);
        verify(contributionRepository).save(captor.capture());
        GoalContribution saved = captor.getValue();
        assertThat(saved.getGoal()).isSameAs(goal);
        assertThat(saved.getAmount()).isEqualByComparingTo("50.00");
        assertThat(saved.getDate()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(saved.getNotes()).isEqualTo("Bônus");

        assertThat(goal.getCurrentAmount()).isEqualByComparingTo("150.00");
        assertThat(response.currentAmount()).isEqualByComparingTo("150.00");
        assertThat(response.percentage()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("addContribution: valor negativo (retirada) deve reduzir o saldo")
    void addContribution_withdrawal_shouldDecreaseBalance() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "100.00");
        stubFindOwned(goal);
        stubSaveReturnsArgument();

        goalService.addContribution(GOAL_ID, USER_ID,
                new GoalContributionRequest(new BigDecimal("-30.00"), LocalDate.of(2026, 3, 10), null));

        assertThat(goal.getCurrentAmount()).isEqualByComparingTo("70.00");
        verify(contributionRepository).save(any(GoalContribution.class));
    }

    @Test
    @DisplayName("addContribution: retirada total que zera o saldo deve ser permitida")
    void addContribution_withdrawal_shouldAllowZeroBalance() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "100.00");
        stubFindOwned(goal);
        stubSaveReturnsArgument();

        goalService.addContribution(GOAL_ID, USER_ID,
                new GoalContributionRequest(new BigDecimal("-100.00"), LocalDate.of(2026, 3, 10), null));

        assertThat(goal.getCurrentAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("addContribution: retirada maior que o saldo deve lançar BusinessRuleException")
    void addContribution_shouldThrowBusinessRule_whenBalanceWouldBeNegative() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "100.00");
        stubFindOwned(goal);

        assertThatThrownBy(() -> goalService.addContribution(GOAL_ID, USER_ID,
                new GoalContributionRequest(new BigDecimal("-100.01"), LocalDate.of(2026, 3, 10), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A retirada não pode deixar o saldo da meta negativo");

        assertThat(goal.getCurrentAmount()).isEqualByComparingTo("100.00");
        verify(contributionRepository, never()).save(any(GoalContribution.class));
        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("addContribution: valor zero deve lançar BusinessRuleException")
    void addContribution_shouldThrowBusinessRule_whenAmountIsZero() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "100.00");
        stubFindOwned(goal);

        assertThatThrownBy(() -> goalService.addContribution(GOAL_ID, USER_ID,
                new GoalContributionRequest(BigDecimal.ZERO, LocalDate.of(2026, 3, 10), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O valor da contribuição não pode ser zero");

        verify(contributionRepository, never()).save(any(GoalContribution.class));
    }

    @Test
    @DisplayName("addContribution: meta que não é SAVINGS deve lançar BusinessRuleException")
    void addContribution_shouldThrowBusinessRule_whenGoalIsNotSavings() {
        Goal goal = goal(GOAL_ID, GoalType.EXPENSE, "1000.00", null);
        stubFindOwned(goal);

        assertThatThrownBy(() -> goalService.addContribution(GOAL_ID, USER_ID,
                new GoalContributionRequest(BigDecimal.TEN, LocalDate.of(2026, 3, 10), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Contribuições só são permitidas para metas do tipo SAVINGS");

        verify(contributionRepository, never()).save(any(GoalContribution.class));
        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("addContribution: meta cancelada deve lançar BusinessRuleException")
    void addContribution_shouldThrowBusinessRule_whenGoalIsCancelled() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "100.00");
        goal.setStatus(GoalStatus.CANCELLED);
        stubFindOwned(goal);

        assertThatThrownBy(() -> goalService.addContribution(GOAL_ID, USER_ID,
                new GoalContributionRequest(BigDecimal.TEN, LocalDate.of(2026, 3, 10), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Não é possível contribuir para uma meta cancelada");

        verify(contributionRepository, never()).save(any(GoalContribution.class));
    }

    @Test
    @DisplayName("addContribution: deve lançar ResourceNotFoundException quando a meta não existe")
    void addContribution_shouldThrowNotFound() {
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.addContribution(GOAL_ID, USER_ID,
                new GoalContributionRequest(BigDecimal.TEN, LocalDate.of(2026, 3, 10), null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(GOAL_NOT_FOUND);

        verifyNoInteractions(contributionRepository);
    }

    // ---------- deleteContribution ----------

    private GoalContribution contribution(Goal goal, String amount) {
        GoalContribution contribution = new GoalContribution(
                goal, new BigDecimal(amount), LocalDate.of(2026, 3, 10), null);
        contribution.setId(CONTRIBUTION_ID);
        return contribution;
    }

    @Test
    @DisplayName("deleteContribution: deve estornar o valor do saldo e excluir a contribuição")
    void deleteContribution_shouldRevertBalanceAndDelete() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "150.00");
        GoalContribution contribution = contribution(goal, "50.00");
        when(contributionRepository.findByIdAndGoalUserId(CONTRIBUTION_ID, USER_ID))
                .thenReturn(Optional.of(contribution));

        goalService.deleteContribution(GOAL_ID, CONTRIBUTION_ID, USER_ID);

        assertThat(goal.getCurrentAmount()).isEqualByComparingTo("100.00");
        verify(goalRepository).save(goal);
        verify(contributionRepository).delete(contribution);
    }

    @Test
    @DisplayName("deleteContribution: excluir uma retirada deve devolver o valor ao saldo")
    void deleteContribution_ofWithdrawal_shouldIncreaseBalance() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "70.00");
        GoalContribution contribution = contribution(goal, "-30.00");
        when(contributionRepository.findByIdAndGoalUserId(CONTRIBUTION_ID, USER_ID))
                .thenReturn(Optional.of(contribution));

        goalService.deleteContribution(GOAL_ID, CONTRIBUTION_ID, USER_ID);

        assertThat(goal.getCurrentAmount()).isEqualByComparingTo("100.00");
        verify(contributionRepository).delete(contribution);
    }

    @Test
    @DisplayName("deleteContribution: deve lançar ResourceNotFoundException quando a contribuição não existe")
    void deleteContribution_shouldThrowNotFound() {
        when(contributionRepository.findByIdAndGoalUserId(CONTRIBUTION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.deleteContribution(GOAL_ID, CONTRIBUTION_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(CONTRIBUTION_NOT_FOUND);

        verify(contributionRepository, never()).delete(any(GoalContribution.class));
        verifyNoInteractions(goalRepository);
    }

    @Test
    @DisplayName("deleteContribution: contribuição de outra meta deve lançar BusinessRuleException")
    void deleteContribution_shouldThrowBusinessRule_whenContributionBelongsToAnotherGoal() {
        Goal otherGoal = goal(99L, GoalType.SAVINGS, "1000.00", "150.00");
        GoalContribution contribution = contribution(otherGoal, "50.00");
        when(contributionRepository.findByIdAndGoalUserId(CONTRIBUTION_ID, USER_ID))
                .thenReturn(Optional.of(contribution));

        assertThatThrownBy(() -> goalService.deleteContribution(GOAL_ID, CONTRIBUTION_ID, USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Contribuição não pertence a esta meta");

        assertThat(otherGoal.getCurrentAmount()).isEqualByComparingTo("150.00");
        verify(contributionRepository, never()).delete(any(GoalContribution.class));
        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("deleteContribution: exclusão que deixaria o saldo negativo deve lançar BusinessRuleException")
    void deleteContribution_shouldThrowBusinessRule_whenBalanceWouldBeNegative() {
        Goal goal = goal(GOAL_ID, GoalType.SAVINGS, "1000.00", "20.00");
        GoalContribution contribution = contribution(goal, "50.00");
        when(contributionRepository.findByIdAndGoalUserId(CONTRIBUTION_ID, USER_ID))
                .thenReturn(Optional.of(contribution));

        assertThatThrownBy(() -> goalService.deleteContribution(GOAL_ID, CONTRIBUTION_ID, USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A exclusão desta contribuição deixaria o saldo da meta negativo");

        assertThat(goal.getCurrentAmount()).isEqualByComparingTo("20.00");
        verify(contributionRepository, never()).delete(any(GoalContribution.class));
        verify(goalRepository, never()).save(any(Goal.class));
    }
}