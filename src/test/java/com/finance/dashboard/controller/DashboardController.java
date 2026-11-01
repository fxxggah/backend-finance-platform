package com.finance.dashboard.controller;

import com.finance.common.enums.Granularity;
import com.finance.common.enums.Period;
import com.finance.common.enums.TransactionType;
import com.finance.dashboard.dto.CategoryExpenseDTO;
import com.finance.dashboard.dto.DashboardSummaryDTO;
import com.finance.dashboard.dto.EvolutionDTO;
import com.finance.dashboard.dto.EvolutionPointDTO;
import com.finance.dashboard.dto.PreviousSummaryDTO;
import com.finance.dashboard.dto.TransactionRecentDTO;
import com.finance.dashboard.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    private static final String URL = "/api/v1/dashboard";
    private static final Long USER_ID = 1L;

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    // O userId vem do subject do JWT
    private static RequestPostProcessor user1() {
        return jwt().jwt(j -> j.subject(String.valueOf(USER_ID)));
    }

    private static DashboardSummaryDTO sampleSummary() {
        return new DashboardSummaryDTO(Period.MONTH, FROM, TO,
                new BigDecimal("1200.00"), new BigDecimal("300.00"), new BigDecimal("900.00"),
                new PreviousSummaryDTO(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                        new BigDecimal("20.0000"), new BigDecimal("-25.0000")));
    }

    private static CategoryExpenseDTO sampleExpense() {
        return new CategoryExpenseDTO(1L, "Mercado", "#3B82F6", "shopping-cart",
                new BigDecimal("300.00"), new BigDecimal("75.00"), 4L);
    }

    private static EvolutionDTO sampleEvolution() {
        return new EvolutionDTO(Granularity.DAY, List.of(
                new EvolutionPointDTO("01/10", FROM, FROM,
                        new BigDecimal("100.00"), new BigDecimal("40.00"), new BigDecimal("60.00"))));
    }

    private static TransactionRecentDTO sampleRecent() {
        return new TransactionRecentDTO(5L, new BigDecimal("89.90"), LocalDate.of(2026, 10, 15),
                TransactionType.EXPENSE, "Mercado", "Conta corrente", "Nubank");
    }

    // ---------- GET /summary ----------

    @Test
    @DisplayName("GET /summary: deve usar MONTH como período padrão e o userId do JWT")
    void summary_shouldUseDefaultPeriod() throws Exception {
        when(dashboardService.getSummary(USER_ID, Period.MONTH, null, null, null, null, null))
                .thenReturn(sampleSummary());

        mockMvc.perform(get(URL + "/summary").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MONTH"))
                .andExpect(jsonPath("$.from").value("2026-10-01"))
                .andExpect(jsonPath("$.to").value("2026-10-31"))
                .andExpect(jsonPath("$.totalIncome").value(1200.0))
                .andExpect(jsonPath("$.totalExpense").value(300.0))
                .andExpect(jsonPath("$.balance").value(900.0))
                .andExpect(jsonPath("$.previous.from").value("2026-09-01"))
                .andExpect(jsonPath("$.previous.to").value("2026-09-30"))
                .andExpect(jsonPath("$.previous.incomeChangePct").value(20.0))
                .andExpect(jsonPath("$.previous.expenseChangePct").value(-25.0));

        verify(dashboardService).getSummary(USER_ID, Period.MONTH, null, null, null, null, null);
    }

    @Test
    @DisplayName("GET /summary: deve repassar período, datas e filtros ao service")
    void summary_shouldPassAllParameters() throws Exception {
        when(dashboardService.getSummary(USER_ID, Period.CUSTOM, LocalDate.of(2026, 10, 15), FROM, TO, 3L, 4L))
                .thenReturn(sampleSummary());

        mockMvc.perform(get(URL + "/summary").with(user1())
                        .param("period", "CUSTOM")
                        .param("date", "2026-10-15")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31")
                        .param("accountId", "3")
                        .param("creditCardId", "4"))
                .andExpect(status().isOk());

        verify(dashboardService).getSummary(USER_ID, Period.CUSTOM, LocalDate.of(2026, 10, 15), FROM, TO, 3L, 4L);
    }

    @Test
    @DisplayName("GET /summary: variação percentual nula deve ser serializada como nula")
    void summary_shouldSerializeNullChange() throws Exception {
        DashboardSummaryDTO summary = new DashboardSummaryDTO(Period.MONTH, FROM, TO,
                new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"),
                new PreviousSummaryDTO(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null));
        when(dashboardService.getSummary(USER_ID, Period.MONTH, null, null, null, null, null)).thenReturn(summary);

        mockMvc.perform(get(URL + "/summary").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previous.incomeChangePct").doesNotExist())
                .andExpect(jsonPath("$.previous.expenseChangePct").doesNotExist());
    }

    @Test
    @DisplayName("GET /summary: deve retornar 400 para período inexistente")
    void summary_shouldReturnBadRequest_whenPeriodIsInvalid() throws Exception {
        mockMvc.perform(get(URL + "/summary").param("period", "DECADE").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }

    @ParameterizedTest(name = "date={0}")
    @ValueSource(strings = {"15/10/2026", "2026-13-01", "abc"})
    @DisplayName("GET /summary: deve retornar 400 para data em formato inválido")
    void summary_shouldReturnBadRequest_whenDateIsInvalid(String date) throws Exception {
        mockMvc.perform(get(URL + "/summary").param("date", date).with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }

    @Test
    @DisplayName("GET /summary: deve retornar 400 quando accountId não é numérico")
    void summary_shouldReturnBadRequest_whenAccountIdIsNotNumeric() throws Exception {
        mockMvc.perform(get(URL + "/summary").param("accountId", "abc").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }

    // ---------- GET /expenses-by-category ----------

    @Test
    @DisplayName("GET /expenses-by-category: deve retornar as despesas por categoria com percentual")
    void expensesByCategory_shouldReturnList() throws Exception {
        when(dashboardService.getExpensesByCategory(USER_ID, Period.MONTH, null, null, null, null, null))
                .thenReturn(List.of(sampleExpense()));

        mockMvc.perform(get(URL + "/expenses-by-category").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].name").value("Mercado"))
                .andExpect(jsonPath("$[0].color").value("#3B82F6"))
                .andExpect(jsonPath("$[0].icon").value("shopping-cart"))
                .andExpect(jsonPath("$[0].total").value(300.0))
                .andExpect(jsonPath("$[0].percentage").value(75.0))
                .andExpect(jsonPath("$[0].count").value(4));
    }

    @Test
    @DisplayName("GET /expenses-by-category: deve repassar período, datas e filtros ao service")
    void expensesByCategory_shouldPassAllParameters() throws Exception {
        when(dashboardService.getExpensesByCategory(USER_ID, Period.YEAR, LocalDate.of(2026, 6, 1), null, null, 3L, 4L))
                .thenReturn(List.of());

        mockMvc.perform(get(URL + "/expenses-by-category").with(user1())
                        .param("period", "YEAR")
                        .param("date", "2026-06-01")
                        .param("accountId", "3")
                        .param("creditCardId", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(dashboardService).getExpensesByCategory(
                USER_ID, Period.YEAR, LocalDate.of(2026, 6, 1), null, null, 3L, 4L);
    }

    @Test
    @DisplayName("GET /expenses-by-category: deve retornar 400 para período inexistente")
    void expensesByCategory_shouldReturnBadRequest_whenPeriodIsInvalid() throws Exception {
        mockMvc.perform(get(URL + "/expenses-by-category").param("period", "DECADE").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }

    // ---------- GET /evolution ----------

    @Test
    @DisplayName("GET /evolution: deve retornar a granularidade e os pontos da evolução")
    void evolution_shouldReturnPoints() throws Exception {
        when(dashboardService.getEvolution(USER_ID, Period.MONTH, null, null, null, null, null))
                .thenReturn(sampleEvolution());

        mockMvc.perform(get(URL + "/evolution").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("DAY"))
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.points[0].label").value("01/10"))
                .andExpect(jsonPath("$.points[0].from").value("2026-10-01"))
                .andExpect(jsonPath("$.points[0].to").value("2026-10-01"))
                .andExpect(jsonPath("$.points[0].income").value(100.0))
                .andExpect(jsonPath("$.points[0].expense").value(40.0))
                .andExpect(jsonPath("$.points[0].balance").value(60.0));
    }

    @Test
    @DisplayName("GET /evolution: deve repassar período, datas e filtros ao service")
    void evolution_shouldPassAllParameters() throws Exception {
        when(dashboardService.getEvolution(USER_ID, Period.CUSTOM, null, FROM, TO, 3L, 4L))
                .thenReturn(sampleEvolution());

        mockMvc.perform(get(URL + "/evolution").with(user1())
                        .param("period", "CUSTOM")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31")
                        .param("accountId", "3")
                        .param("creditCardId", "4"))
                .andExpect(status().isOk());

        verify(dashboardService).getEvolution(USER_ID, Period.CUSTOM, null, FROM, TO, 3L, 4L);
    }

    @Test
    @DisplayName("GET /evolution: deve retornar 400 quando creditCardId não é numérico")
    void evolution_shouldReturnBadRequest_whenCreditCardIdIsNotNumeric() throws Exception {
        mockMvc.perform(get(URL + "/evolution").param("creditCardId", "abc").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }

    // ---------- GET /recent-transactions ----------

    @Test
    @DisplayName("GET /recent-transactions: deve usar limite 5 por padrão")
    void recentTransactions_shouldUseDefaultLimit() throws Exception {
        when(dashboardService.getRecentTransactions(USER_ID, 5)).thenReturn(List.of(sampleRecent()));

        mockMvc.perform(get(URL + "/recent-transactions").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].amount").value(89.9))
                .andExpect(jsonPath("$[0].date").value("2026-10-15"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[0].categoryName").value("Mercado"))
                .andExpect(jsonPath("$[0].accountName").value("Conta corrente"))
                .andExpect(jsonPath("$[0].creditCardName").value("Nubank"));

        verify(dashboardService).getRecentTransactions(USER_ID, 5);
    }

    @Test
    @DisplayName("GET /recent-transactions: deve repassar o limite informado")
    void recentTransactions_shouldPassLimit() throws Exception {
        when(dashboardService.getRecentTransactions(USER_ID, 10)).thenReturn(List.of());

        mockMvc.perform(get(URL + "/recent-transactions").param("limit", "10").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(dashboardService).getRecentTransactions(USER_ID, 10);
    }

    @Test
    @DisplayName("GET /recent-transactions: deve retornar 400 quando o limite não é numérico")
    void recentTransactions_shouldReturnBadRequest_whenLimitIsNotNumeric() throws Exception {
        mockMvc.perform(get(URL + "/recent-transactions").param("limit", "abc").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }

    // ---------- autenticação ----------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/summary", "/expenses-by-category", "/evolution", "/recent-transactions"})
    @DisplayName("Todos os endpoints: devem retornar 401 sem autenticação")
    void allEndpoints_shouldReturnUnauthorized_whenNotAuthenticated(String path) throws Exception {
        mockMvc.perform(get(URL + path))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dashboardService);
    }
}