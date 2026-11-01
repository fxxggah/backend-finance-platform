package com.finance.dashboard.service;

import com.finance.account.entity.Account;
import com.finance.category.entity.Category;
import com.finance.common.enums.Granularity;
import com.finance.common.enums.Period;
import com.finance.common.enums.TransactionType;
import com.finance.creditcard.entity.CreditCard;
import com.finance.dashboard.dto.CategoryExpenseDTO;
import com.finance.dashboard.dto.DashboardSummaryDTO;
import com.finance.dashboard.dto.EvolutionDTO;
import com.finance.dashboard.dto.EvolutionPointDTO;
import com.finance.dashboard.dto.PeriodRange;
import com.finance.dashboard.dto.TransactionRecentDTO;
import com.finance.transaction.entity.Transaction;
import com.finance.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long USER_ID = 1L;

    private static final TransactionType INCOME = TransactionType.INCOME;
    private static final TransactionType EXPENSE = TransactionType.EXPENSE;

    private static final LocalDate REFERENCE = LocalDate.of(2026, 10, 15);
    private static final PeriodRange CURRENT = new PeriodRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    private static final PeriodRange PREVIOUS = new PeriodRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PeriodResolver periodResolver;

    @InjectMocks
    private DashboardService dashboardService;

    // ---------- helpers ----------

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private void stubSum(TransactionType type, PeriodRange range, String value, Long accountId, Long creditCardId) {
        when(transactionRepository.sumAmountByTypeAndPeriodAndFilters(
                USER_ID, type, range.from(), range.to(), accountId, creditCardId))
                .thenReturn(new BigDecimal(value));
    }

    private void stubPeriods(Long accountId, Long creditCardId, String curIncome, String curExpense,
                             String prevIncome, String prevExpense) {
        when(periodResolver.resolveCurrent(Period.MONTH, REFERENCE, null, null)).thenReturn(CURRENT);
        when(periodResolver.resolvePrevious(Period.MONTH, CURRENT)).thenReturn(PREVIOUS);
        stubSum(INCOME, CURRENT, curIncome, accountId, creditCardId);
        stubSum(EXPENSE, CURRENT, curExpense, accountId, creditCardId);
        stubSum(INCOME, PREVIOUS, prevIncome, accountId, creditCardId);
        stubSum(EXPENSE, PREVIOUS, prevExpense, accountId, creditCardId);
    }

    private CategoryExpenseDTO expense(Long id, String name, String total) {
        return new CategoryExpenseDTO(id, name, "#3B82F6", "tag", new BigDecimal(total), BigDecimal.ZERO, 2L);
    }

    private Object[] row(LocalDate date, TransactionType type, String amount) {
        return new Object[]{date, type, new BigDecimal(amount)};
    }

    private List<Object[]> rows(Object[]... rows) {
        return new ArrayList<>(Arrays.asList(rows));
    }

    // ---------- getSummary ----------

    @Test
    @DisplayName("getSummary: deve calcular totais, saldo e variação percentual em relação ao período anterior")
    void getSummary_shouldComputeTotalsBalanceAndChange() {
        stubPeriods(null, null, "1200.00", "300.00", "1000.00", "400.00");

        DashboardSummaryDTO summary = dashboardService.getSummary(
                USER_ID, Period.MONTH, REFERENCE, null, null, null, null);

        assertThat(summary.period()).isEqualTo(Period.MONTH);
        assertThat(summary.from()).isEqualTo(CURRENT.from());
        assertThat(summary.to()).isEqualTo(CURRENT.to());
        assertThat(summary.totalIncome()).isEqualByComparingTo("1200.00");
        assertThat(summary.totalExpense()).isEqualByComparingTo("300.00");
        assertThat(summary.balance()).isEqualByComparingTo("900.00");

        assertThat(summary.previous().from()).isEqualTo(PREVIOUS.from());
        assertThat(summary.previous().to()).isEqualTo(PREVIOUS.to());
        assertThat(summary.previous().incomeChangePct()).isEqualByComparingTo("20");
        assertThat(summary.previous().expenseChangePct()).isEqualByComparingTo("-25");
    }

    @Test
    @DisplayName("getSummary: deve retornar saldo negativo quando as despesas superam as receitas")
    void getSummary_shouldReturnNegativeBalance() {
        stubPeriods(null, null, "100.00", "350.50", "100.00", "100.00");

        DashboardSummaryDTO summary = dashboardService.getSummary(
                USER_ID, Period.MONTH, REFERENCE, null, null, null, null);

        assertThat(summary.balance()).isEqualByComparingTo("-250.50");
    }

    @Test
    @DisplayName("getSummary: variação deve ser nula quando o valor do período anterior é zero")
    void getSummary_shouldReturnNullChange_whenPreviousIsZero() {
        stubPeriods(null, null, "500.00", "200.00", "0.00", "0.00");

        DashboardSummaryDTO summary = dashboardService.getSummary(
                USER_ID, Period.MONTH, REFERENCE, null, null, null, null);

        assertThat(summary.previous().incomeChangePct()).isNull();
        assertThat(summary.previous().expenseChangePct()).isNull();
    }

    @Test
    @DisplayName("getSummary: variação deve ser -100% quando o período atual zerou")
    void getSummary_shouldReturnMinus100_whenCurrentIsZero() {
        stubPeriods(null, null, "0.00", "0.00", "400.00", "200.00");

        DashboardSummaryDTO summary = dashboardService.getSummary(
                USER_ID, Period.MONTH, REFERENCE, null, null, null, null);

        assertThat(summary.previous().incomeChangePct()).isEqualByComparingTo("-100");
        assertThat(summary.previous().expenseChangePct()).isEqualByComparingTo("-100");
        assertThat(summary.balance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("getSummary: deve arredondar a variação com HALF_EVEN em 4 casas antes de multiplicar por 100")
    void getSummary_shouldRoundChange() {
        // (100 - 300) / 300 = -0.6667 -> -66.67
        stubPeriods(null, null, "100.00", "100.00", "300.00", "100.00");

        DashboardSummaryDTO summary = dashboardService.getSummary(
                USER_ID, Period.MONTH, REFERENCE, null, null, null, null);

        assertThat(summary.previous().incomeChangePct()).isEqualByComparingTo("-66.67");
        assertThat(summary.previous().expenseChangePct()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("getSummary: deve repassar os filtros de conta e cartão ao repository")
    void getSummary_shouldPassFilters() {
        stubPeriods(3L, 4L, "10.00", "5.00", "10.00", "5.00");

        DashboardSummaryDTO summary = dashboardService.getSummary(
                USER_ID, Period.MONTH, REFERENCE, null, null, 3L, 4L);

        assertThat(summary.totalIncome()).isEqualByComparingTo("10.00");
        verify(transactionRepository).sumAmountByTypeAndPeriodAndFilters(
                USER_ID, INCOME, CURRENT.from(), CURRENT.to(), 3L, 4L);
        verify(transactionRepository).sumAmountByTypeAndPeriodAndFilters(
                USER_ID, EXPENSE, PREVIOUS.from(), PREVIOUS.to(), 3L, 4L);
    }

    // ---------- getExpensesByCategory ----------

    @Test
    @DisplayName("getExpensesByCategory: deve calcular o percentual de cada categoria sobre o total")
    void getExpensesByCategory_shouldComputePercentages() {
        when(periodResolver.resolveCurrent(Period.MONTH, REFERENCE, null, null)).thenReturn(CURRENT);
        when(transactionRepository.findExpensesByCategory(USER_ID, CURRENT.from(), CURRENT.to(), null, null))
                .thenReturn(List.of(expense(1L, "Mercado", "300.00"), expense(2L, "Transporte", "100.00")));

        List<CategoryExpenseDTO> result = dashboardService.getExpensesByCategory(
                USER_ID, Period.MONTH, REFERENCE, null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).percentage()).isEqualByComparingTo("75.00");
        assertThat(result.get(1).percentage()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("getExpensesByCategory: deve preservar os demais campos de cada categoria")
    void getExpensesByCategory_shouldPreserveOtherFields() {
        when(periodResolver.resolveCurrent(Period.MONTH, REFERENCE, null, null)).thenReturn(CURRENT);
        when(transactionRepository.findExpensesByCategory(USER_ID, CURRENT.from(), CURRENT.to(), null, null))
                .thenReturn(List.of(expense(1L, "Mercado", "300.00")));

        CategoryExpenseDTO result = dashboardService.getExpensesByCategory(
                USER_ID, Period.MONTH, REFERENCE, null, null, null, null).getFirst();

        assertThat(result.categoryId()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Mercado");
        assertThat(result.color()).isEqualTo("#3B82F6");
        assertThat(result.icon()).isEqualTo("tag");
        assertThat(result.total()).isEqualByComparingTo("300.00");
        assertThat(result.count()).isEqualTo(2L);
        assertThat(result.percentage()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("getExpensesByCategory: deve arredondar percentuais para 2 casas decimais")
    void getExpensesByCategory_shouldRoundPercentages() {
        when(periodResolver.resolveCurrent(Period.MONTH, REFERENCE, null, null)).thenReturn(CURRENT);
        when(transactionRepository.findExpensesByCategory(USER_ID, CURRENT.from(), CURRENT.to(), null, null))
                .thenReturn(List.of(expense(1L, "A", "100.00"), expense(2L, "B", "200.00")));

        List<CategoryExpenseDTO> result = dashboardService.getExpensesByCategory(
                USER_ID, Period.MONTH, REFERENCE, null, null, null, null);

        assertThat(result.get(0).percentage()).isEqualByComparingTo("33.33");
        assertThat(result.get(1).percentage()).isEqualByComparingTo("66.67");
    }

    @Test
    @DisplayName("getExpensesByCategory: deve devolver a lista original quando o total é zero")
    void getExpensesByCategory_shouldReturnOriginalList_whenTotalIsZero() {
        List<CategoryExpenseDTO> original = List.of(expense(1L, "Mercado", "0.00"));
        when(periodResolver.resolveCurrent(Period.MONTH, REFERENCE, null, null)).thenReturn(CURRENT);
        when(transactionRepository.findExpensesByCategory(USER_ID, CURRENT.from(), CURRENT.to(), null, null))
                .thenReturn(original);

        List<CategoryExpenseDTO> result = dashboardService.getExpensesByCategory(
                USER_ID, Period.MONTH, REFERENCE, null, null, null, null);

        assertThat(result).isSameAs(original);
    }

    @Test
    @DisplayName("getExpensesByCategory: deve devolver lista vazia quando não há despesas")
    void getExpensesByCategory_shouldReturnEmpty_whenNoExpenses() {
        when(periodResolver.resolveCurrent(Period.MONTH, REFERENCE, null, null)).thenReturn(CURRENT);
        when(transactionRepository.findExpensesByCategory(USER_ID, CURRENT.from(), CURRENT.to(), null, null))
                .thenReturn(List.of());

        assertThat(dashboardService.getExpensesByCategory(
                USER_ID, Period.MONTH, REFERENCE, null, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("getExpensesByCategory: deve repassar os filtros de conta e cartão ao repository")
    void getExpensesByCategory_shouldPassFilters() {
        when(periodResolver.resolveCurrent(Period.MONTH, REFERENCE, null, null)).thenReturn(CURRENT);
        when(transactionRepository.findExpensesByCategory(USER_ID, CURRENT.from(), CURRENT.to(), 3L, 4L))
                .thenReturn(List.of(expense(1L, "Mercado", "50.00")));

        List<CategoryExpenseDTO> result = dashboardService.getExpensesByCategory(
                USER_ID, Period.MONTH, REFERENCE, null, null, 3L, 4L);

        assertThat(result).hasSize(1);
        verify(transactionRepository).findExpensesByCategory(USER_ID, CURRENT.from(), CURRENT.to(), 3L, 4L);
    }

    // ---------- getEvolution ----------

    @Test
    @DisplayName("getEvolution: granularidade DAY deve gerar um ponto por dia com receita, despesa e saldo")
    void getEvolution_day_shouldBuildOnePointPerDay() {
        PeriodRange range = new PeriodRange(d(2026, 10, 1), d(2026, 10, 3));
        when(periodResolver.resolveCurrent(Period.CUSTOM, null, range.from(), range.to())).thenReturn(range);
        when(periodResolver.resolveGranularity(range)).thenReturn(Granularity.DAY);
        when(transactionRepository.findEvolutionData(USER_ID, range.from(), range.to(), null, null))
                .thenReturn(rows(
                        row(d(2026, 10, 1), EXPENSE, "100.00"),
                        row(d(2026, 10, 2), INCOME, "500.00"),
                        row(d(2026, 10, 3), EXPENSE, "40.00")));

        EvolutionDTO evolution = dashboardService.getEvolution(
                USER_ID, Period.CUSTOM, null, range.from(), range.to(), null, null);

        assertThat(evolution.granularity()).isEqualTo(Granularity.DAY);
        assertThat(evolution.points()).extracting(EvolutionPointDTO::label)
                .containsExactly("01/10", "02/10", "03/10");
        assertThat(evolution.points()).extracting(EvolutionPointDTO::from)
                .containsExactly(d(2026, 10, 1), d(2026, 10, 2), d(2026, 10, 3));
        assertThat(evolution.points()).extracting(EvolutionPointDTO::to)
                .containsExactly(d(2026, 10, 1), d(2026, 10, 2), d(2026, 10, 3));

        EvolutionPointDTO first = evolution.points().get(0);
        assertThat(first.income()).isEqualByComparingTo("0");
        assertThat(first.expense()).isEqualByComparingTo("100.00");
        assertThat(first.balance()).isEqualByComparingTo("-100.00");

        EvolutionPointDTO second = evolution.points().get(1);
        assertThat(second.income()).isEqualByComparingTo("500.00");
        assertThat(second.balance()).isEqualByComparingTo("500.00");

        assertThat(evolution.points().get(2).balance()).isEqualByComparingTo("-40.00");
    }

    @Test
    @DisplayName("getEvolution: granularidade WEEK deve agrupar de segunda a domingo e truncar a última semana")
    void getEvolution_week_shouldGroupByWeekAndTruncateLast() {
        // 01/10/2026 é quinta-feira; o intervalo termina numa quarta (14/10)
        PeriodRange range = new PeriodRange(d(2026, 10, 1), d(2026, 10, 14));
        when(periodResolver.resolveCurrent(Period.CUSTOM, null, range.from(), range.to())).thenReturn(range);
        when(periodResolver.resolveGranularity(range)).thenReturn(Granularity.WEEK);
        when(transactionRepository.findEvolutionData(USER_ID, range.from(), range.to(), null, null))
                .thenReturn(rows(
                        row(d(2026, 10, 2), INCOME, "100.00"),
                        row(d(2026, 10, 4), INCOME, "50.00"),
                        row(d(2026, 10, 5), EXPENSE, "30.00"),
                        row(d(2026, 10, 14), EXPENSE, "10.00")));

        EvolutionDTO evolution = dashboardService.getEvolution(
                USER_ID, Period.CUSTOM, null, range.from(), range.to(), null, null);

        assertThat(evolution.granularity()).isEqualTo(Granularity.WEEK);
        assertThat(evolution.points()).extracting(EvolutionPointDTO::label)
                .containsExactly("01/10 - 04/10", "05/10 - 11/10", "12/10 - 14/10");
        assertThat(evolution.points()).extracting(EvolutionPointDTO::from)
                .containsExactly(d(2026, 10, 1), d(2026, 10, 5), d(2026, 10, 12));
        assertThat(evolution.points()).extracting(EvolutionPointDTO::to)
                .containsExactly(d(2026, 10, 4), d(2026, 10, 11), d(2026, 10, 14));

        assertThat(evolution.points().get(0).income()).isEqualByComparingTo("150.00");
        assertThat(evolution.points().get(0).expense()).isEqualByComparingTo("0");
        assertThat(evolution.points().get(1).expense()).isEqualByComparingTo("30.00");
        assertThat(evolution.points().get(1).balance()).isEqualByComparingTo("-30.00");
        assertThat(evolution.points().get(2).expense()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("getEvolution: granularidade MONTH deve agrupar por mês e truncar o primeiro e o último mês")
    void getEvolution_month_shouldGroupByMonth() {
        PeriodRange range = new PeriodRange(d(2026, 1, 15), d(2026, 3, 10));
        when(periodResolver.resolveCurrent(Period.CUSTOM, null, range.from(), range.to())).thenReturn(range);
        when(periodResolver.resolveGranularity(range)).thenReturn(Granularity.MONTH);
        when(transactionRepository.findEvolutionData(USER_ID, range.from(), range.to(), null, null))
                .thenReturn(rows(
                        row(d(2026, 1, 20), INCOME, "100.00"),
                        row(d(2026, 2, 5), INCOME, "200.00"),
                        row(d(2026, 2, 28), EXPENSE, "80.00"),
                        row(d(2026, 3, 10), EXPENSE, "50.00")));

        EvolutionDTO evolution = dashboardService.getEvolution(
                USER_ID, Period.CUSTOM, null, range.from(), range.to(), null, null);

        assertThat(evolution.granularity()).isEqualTo(Granularity.MONTH);
        assertThat(evolution.points()).extracting(EvolutionPointDTO::label)
                .containsExactly("01/2026", "02/2026", "03/2026");
        assertThat(evolution.points()).extracting(EvolutionPointDTO::from)
                .containsExactly(d(2026, 1, 15), d(2026, 2, 1), d(2026, 3, 1));
        assertThat(evolution.points()).extracting(EvolutionPointDTO::to)
                .containsExactly(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 10));

        assertThat(evolution.points().get(0).balance()).isEqualByComparingTo("100.00");
        assertThat(evolution.points().get(1).income()).isEqualByComparingTo("200.00");
        assertThat(evolution.points().get(1).expense()).isEqualByComparingTo("80.00");
        assertThat(evolution.points().get(1).balance()).isEqualByComparingTo("120.00");
        assertThat(evolution.points().get(2).balance()).isEqualByComparingTo("-50.00");
    }

    @Test
    @DisplayName("getEvolution: deve gerar pontos zerados quando não há movimentações")
    void getEvolution_shouldReturnZeroedPoints_whenNoData() {
        PeriodRange range = new PeriodRange(d(2026, 10, 1), d(2026, 10, 2));
        when(periodResolver.resolveCurrent(Period.CUSTOM, null, range.from(), range.to())).thenReturn(range);
        when(periodResolver.resolveGranularity(range)).thenReturn(Granularity.DAY);
        when(transactionRepository.findEvolutionData(USER_ID, range.from(), range.to(), null, null))
                .thenReturn(rows());

        EvolutionDTO evolution = dashboardService.getEvolution(
                USER_ID, Period.CUSTOM, null, range.from(), range.to(), null, null);

        assertThat(evolution.points()).hasSize(2);
        assertThat(evolution.points()).allSatisfy(point -> {
            assertThat(point.income()).isEqualByComparingTo("0");
            assertThat(point.expense()).isEqualByComparingTo("0");
            assertThat(point.balance()).isEqualByComparingTo("0");
        });
    }

    @Test
    @DisplayName("getEvolution: deve repassar os filtros de conta e cartão ao repository")
    void getEvolution_shouldPassFilters() {
        PeriodRange range = new PeriodRange(d(2026, 10, 1), d(2026, 10, 1));
        when(periodResolver.resolveCurrent(Period.DAY, REFERENCE, null, null)).thenReturn(range);
        when(periodResolver.resolveGranularity(range)).thenReturn(Granularity.DAY);
        when(transactionRepository.findEvolutionData(USER_ID, range.from(), range.to(), 3L, 4L))
                .thenReturn(rows());

        EvolutionDTO evolution = dashboardService.getEvolution(
                USER_ID, Period.DAY, REFERENCE, null, null, 3L, 4L);

        assertThat(evolution.points()).hasSize(1);
        verify(transactionRepository).findEvolutionData(USER_ID, range.from(), range.to(), 3L, 4L);
    }

    // ---------- getRecentTransactions ----------

    @Test
    @DisplayName("getRecentTransactions: deve mapear a transação com categoria, conta e cartão")
    void getRecentTransactions_shouldMapTransaction() {
        Category category = mock(Category.class);
        Account account = mock(Account.class);
        CreditCard card = mock(CreditCard.class);
        when(category.getName()).thenReturn("Mercado");
        when(account.getName()).thenReturn("Conta corrente");
        when(card.getName()).thenReturn("Nubank");

        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(5L);
        when(transaction.getAmount()).thenReturn(new BigDecimal("89.90"));
        when(transaction.getDate()).thenReturn(d(2026, 10, 15));
        when(transaction.getType()).thenReturn(EXPENSE);
        when(transaction.getCategory()).thenReturn(category);
        when(transaction.getAccount()).thenReturn(account);
        when(transaction.getCreditCard()).thenReturn(card);
        when(transactionRepository.findAllByUserIdAndDeletedFalseWithFetch(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction)));

        List<TransactionRecentDTO> result = dashboardService.getRecentTransactions(USER_ID, 5);

        assertThat(result).hasSize(1);
        TransactionRecentDTO dto = result.getFirst();
        assertThat(dto.id()).isEqualTo(5L);
        assertThat(dto.amount()).isEqualByComparingTo("89.90");
        assertThat(dto.date()).isEqualTo(d(2026, 10, 15));
        assertThat(dto.type()).isEqualTo(EXPENSE);
        assertThat(dto.categoryName()).isEqualTo("Mercado");
        assertThat(dto.accountName()).isEqualTo("Conta corrente");
        assertThat(dto.creditCardName()).isEqualTo("Nubank");
    }

    @Test
    @DisplayName("getRecentTransactions: deve devolver nomes nulos quando categoria, conta e cartão não existem")
    void getRecentTransactions_shouldReturnNullNames_whenRelationsAreNull() {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(6L);
        when(transaction.getType()).thenReturn(INCOME);
        when(transactionRepository.findAllByUserIdAndDeletedFalseWithFetch(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction)));

        TransactionRecentDTO dto = dashboardService.getRecentTransactions(USER_ID, 5).getFirst();

        assertThat(dto.id()).isEqualTo(6L);
        assertThat(dto.type()).isEqualTo(INCOME);
        assertThat(dto.categoryName()).isNull();
        assertThat(dto.accountName()).isNull();
        assertThat(dto.creditCardName()).isNull();
    }

    @Test
    @DisplayName("getRecentTransactions: deve paginar pelo limite e ordenar por data e criação decrescentes")
    void getRecentTransactions_shouldUseLimitAndSortDescending() {
        when(transactionRepository.findAllByUserIdAndDeletedFalseWithFetch(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<TransactionRecentDTO> result = dashboardService.getRecentTransactions(USER_ID, 10);

        assertThat(result).isEmpty();
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAllByUserIdAndDeletedFalseWithFetch(eq(USER_ID), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "date", "createdAt"));
    }
}