package com.finance.goal.controller;

import com.finance.common.enums.GoalState;
import com.finance.common.enums.GoalStatus;
import com.finance.common.enums.GoalType;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.goal.dto.request.GoalContributionRequest;
import com.finance.goal.dto.request.GoalRequest;
import com.finance.goal.dto.response.CategorySimpleDto;
import com.finance.goal.dto.response.GoalContributionResponse;
import com.finance.goal.dto.response.GoalResponse;
import com.finance.goal.service.GoalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GoalController.class)
class GoalControllerTest {

    private static final String URL = "/api/v1/goals";
    private static final Long USER_ID = 1L;
    private static final Long GOAL_ID = 10L;
    private static final Long CONTRIBUTION_ID = 100L;

    private static final String VALID_GOAL_BODY = """
            {"type":"SAVINGS","name":"Viagem","description":"Férias","icon":"plane","color":"#3B82F6",
             "targetAmount":5000.00,"startDate":"2026-01-01","endDate":"2026-12-31"}""";

    private static final String VALID_CONTRIBUTION_BODY = """
            {"amount":150.50,"date":"2026-03-10","notes":"Bônus"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoalService goalService;

    // O userId vem do subject do JWT
    private static RequestPostProcessor user1() {
        return jwt().jwt(j -> j.subject(String.valueOf(USER_ID)));
    }

    private static GoalResponse sampleResponse() {
        return new GoalResponse(
                GOAL_ID, GoalType.SAVINGS, "Viagem", "Férias", "plane", "#3B82F6",
                new BigDecimal("5000.00"), new BigDecimal("1000.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                GoalStatus.ACTIVE, GoalState.IN_PROGRESS,
                new BigDecimal("20.00"), new BigDecimal("500.00"),
                new CategorySimpleDto(5L, "Mercado", "#3B82F6", "tag"));
    }

    static Stream<Arguments> invalidGoalBodies() {
        return Stream.of(
                Arguments.of("tipo ausente", """
                        {"name":"Viagem","targetAmount":100}"""),
                Arguments.of("tipo inexistente", """
                        {"type":"NAO_EXISTE","name":"Viagem","targetAmount":100}"""),
                Arguments.of("nome em branco", """
                        {"type":"SAVINGS","name":" ","targetAmount":100}"""),
                Arguments.of("nome ausente", """
                        {"type":"SAVINGS","targetAmount":100}"""),
                Arguments.of("nome com mais de 100 caracteres", """
                        {"type":"SAVINGS","name":"%s","targetAmount":100}""".formatted("a".repeat(101))),
                Arguments.of("descrição com mais de 255 caracteres", """
                        {"type":"SAVINGS","name":"Viagem","description":"%s","targetAmount":100}"""
                        .formatted("a".repeat(256))),
                Arguments.of("ícone com mais de 40 caracteres", """
                        {"type":"SAVINGS","name":"Viagem","icon":"%s","targetAmount":100}"""
                        .formatted("a".repeat(41))),
                Arguments.of("cor com mais de 7 caracteres", """
                        {"type":"SAVINGS","name":"Viagem","color":"#3B82F6FF","targetAmount":100}"""),
                Arguments.of("valor alvo ausente", """
                        {"type":"SAVINGS","name":"Viagem"}"""),
                Arguments.of("valor alvo zero", """
                        {"type":"SAVINGS","name":"Viagem","targetAmount":0}"""),
                Arguments.of("valor alvo negativo", """
                        {"type":"SAVINGS","name":"Viagem","targetAmount":-10}"""),
                Arguments.of("data inválida", """
                        {"type":"SAVINGS","name":"Viagem","targetAmount":100,"startDate":"31/12/2026"}"""));
    }

    static Stream<Arguments> invalidContributionBodies() {
        return Stream.of(
                Arguments.of("valor ausente", """
                        {"date":"2026-03-10"}"""),
                Arguments.of("data ausente", """
                        {"amount":100}"""),
                Arguments.of("data inválida", """
                        {"amount":100,"date":"10/03/2026"}"""),
                Arguments.of("observação com mais de 255 caracteres", """
                        {"amount":100,"date":"2026-03-10","notes":"%s"}""".formatted("a".repeat(256))));
    }

    // ---------- GET /api/v1/goals ----------

    @Test
    @DisplayName("GET /goals: deve retornar a lista sem filtros")
    void list_shouldReturnGoalsWithoutFilters() throws Exception {
        when(goalService.listGoals(USER_ID, null, null)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get(URL).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(GOAL_ID))
                .andExpect(jsonPath("$[0].type").value("SAVINGS"))
                .andExpect(jsonPath("$[0].name").value("Viagem"))
                .andExpect(jsonPath("$[0].targetAmount").value(5000.0))
                .andExpect(jsonPath("$[0].currentAmount").value(1000.0))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].percentage").value(20.0))
                .andExpect(jsonPath("$[0].monthlyNeeded").value(500.0))
                .andExpect(jsonPath("$[0].category.id").value(5))
                .andExpect(jsonPath("$[0].category.name").value("Mercado"));

        verify(goalService).listGoals(USER_ID, null, null);
    }

    @Test
    @DisplayName("GET /goals?type=&status=: deve repassar os filtros ao service")
    void list_shouldPassFilters() throws Exception {
        when(goalService.listGoals(USER_ID, GoalType.EXPENSE, GoalStatus.ACTIVE)).thenReturn(List.of());

        mockMvc.perform(get(URL).param("type", "EXPENSE").param("status", "ACTIVE").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(goalService).listGoals(USER_ID, GoalType.EXPENSE, GoalStatus.ACTIVE);
    }

    @Test
    @DisplayName("GET /goals?type=INVALIDO: deve retornar 400")
    void list_shouldReturnBadRequest_whenTypeIsInvalid() throws Exception {
        mockMvc.perform(get(URL).param("type", "INVALIDO").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    @Test
    @DisplayName("GET /goals?status=INVALIDO: deve retornar 400")
    void list_shouldReturnBadRequest_whenStatusIsInvalid() throws Exception {
        mockMvc.perform(get(URL).param("status", "INVALIDO").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    // ---------- GET /api/v1/goals/{id} ----------

    @Test
    @DisplayName("GET /goals/{id}: deve retornar a meta usando o userId do JWT")
    void get_shouldReturnGoal() throws Exception {
        when(goalService.getGoal(GOAL_ID, USER_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get(URL + "/" + GOAL_ID).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(GOAL_ID))
                .andExpect(jsonPath("$.name").value("Viagem"))
                .andExpect(jsonPath("$.startDate").value("2026-01-01"))
                .andExpect(jsonPath("$.endDate").value("2026-12-31"));

        verify(goalService).getGoal(GOAL_ID, USER_ID);
    }

    @Test
    @DisplayName("GET /goals/{id}: deve retornar 404 quando a meta não for encontrada")
    void get_shouldReturnNotFound() throws Exception {
        when(goalService.getGoal(99L, USER_ID)).thenThrow(new ResourceNotFoundException("Meta não encontrada"));

        mockMvc.perform(get(URL + "/99").with(user1()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /goals/{id}: deve retornar 400 quando o id não é numérico")
    void get_shouldReturnBadRequest_whenIdIsNotNumeric() throws Exception {
        mockMvc.perform(get(URL + "/abc").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    // ---------- POST /api/v1/goals ----------

    @Test
    @DisplayName("POST /goals: deve retornar 201 e repassar o request com o userId do JWT")
    void create_shouldReturnCreated() throws Exception {
        when(goalService.createGoal(eq(USER_ID), any(GoalRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GOAL_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(GOAL_ID))
                .andExpect(jsonPath("$.name").value("Viagem"));

        ArgumentCaptor<GoalRequest> captor = ArgumentCaptor.forClass(GoalRequest.class);
        verify(goalService).createGoal(eq(USER_ID), captor.capture());
        GoalRequest sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(GoalType.SAVINGS);
        assertThat(sent.name()).isEqualTo("Viagem");
        assertThat(sent.description()).isEqualTo("Férias");
        assertThat(sent.icon()).isEqualTo("plane");
        assertThat(sent.color()).isEqualTo("#3B82F6");
        assertThat(sent.targetAmount()).isEqualByComparingTo("5000.00");
        assertThat(sent.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(sent.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("POST /goals: deve aceitar apenas tipo, nome e valor alvo (demais campos opcionais)")
    void create_shouldAcceptMinimalBody() throws Exception {
        when(goalService.createGoal(eq(USER_ID), any(GoalRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SAVINGS","name":"Viagem","targetAmount":100}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /goals: deve ignorar um userId enviado no body e usar o do JWT")
    void create_shouldIgnoreUserIdFromBody() throws Exception {
        when(goalService.createGoal(eq(USER_ID), any(GoalRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SAVINGS","name":"Viagem","targetAmount":100,"userId":999}"""))
                .andExpect(status().isCreated());

        verify(goalService).createGoal(eq(USER_ID), any(GoalRequest.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidGoalBodies")
    @DisplayName("POST /goals: deve retornar 400 para body inválido")
    void create_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    @Test
    @DisplayName("POST /goals: deve retornar erro 4xx quando uma regra de negócio for violada")
    void create_shouldReturnClientError_whenBusinessRuleFails() throws Exception {
        when(goalService.createGoal(eq(USER_ID), any(GoalRequest.class)))
                .thenThrow(new BusinessRuleException("Metas de INCOME/EXPENSE exigem startDate e endDate"));

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"EXPENSE","name":"Limite","targetAmount":100}"""))
                .andExpect(status().is4xxClientError());
    }

    // ---------- PUT /api/v1/goals/{id} ----------

    @Test
    @DisplayName("PUT /goals/{id}: deve retornar 200 com a meta atualizada")
    void update_shouldReturnOk() throws Exception {
        when(goalService.updateGoal(eq(GOAL_ID), eq(USER_ID), any(GoalRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(put(URL + "/" + GOAL_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GOAL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(GOAL_ID))
                .andExpect(jsonPath("$.name").value("Viagem"));

        ArgumentCaptor<GoalRequest> captor = ArgumentCaptor.forClass(GoalRequest.class);
        verify(goalService).updateGoal(eq(GOAL_ID), eq(USER_ID), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Viagem");
        assertThat(captor.getValue().targetAmount()).isEqualByComparingTo("5000.00");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidGoalBodies")
    @DisplayName("PUT /goals/{id}: deve retornar 400 para body inválido")
    void update_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(put(URL + "/" + GOAL_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    @Test
    @DisplayName("PUT /goals/{id}: deve retornar 400 quando o id não é numérico")
    void update_shouldReturnBadRequest_whenIdIsNotNumeric() throws Exception {
        mockMvc.perform(put(URL + "/abc").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GOAL_BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    @Test
    @DisplayName("PUT /goals/{id}: deve retornar 404 quando a meta não for encontrada")
    void update_shouldReturnNotFound() throws Exception {
        when(goalService.updateGoal(eq(99L), eq(USER_ID), any(GoalRequest.class)))
                .thenThrow(new ResourceNotFoundException("Meta não encontrada"));

        mockMvc.perform(put(URL + "/99").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GOAL_BODY))
                .andExpect(status().isNotFound());
    }

    // ---------- PATCH /api/v1/goals/{id}/cancel ----------

    @Test
    @DisplayName("PATCH /goals/{id}/cancel: deve retornar 200 sem corpo e usar o userId do JWT")
    void cancel_shouldReturnOk() throws Exception {
        mockMvc.perform(patch(URL + "/" + GOAL_ID + "/cancel").with(user1()).with(csrf()))
                .andExpect(status().isOk());

        verify(goalService).cancelGoal(GOAL_ID, USER_ID);
    }

    @Test
    @DisplayName("PATCH /goals/{id}/cancel: deve retornar 404 quando a meta não for encontrada")
    void cancel_shouldReturnNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Meta não encontrada"))
                .when(goalService).cancelGoal(99L, USER_ID);

        mockMvc.perform(patch(URL + "/99/cancel").with(user1()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------- DELETE /api/v1/goals/{id} ----------

    @Test
    @DisplayName("DELETE /goals/{id}: deve retornar 204 sem corpo e usar o userId do JWT")
    void delete_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete(URL + "/" + GOAL_ID).with(user1()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(goalService).deleteGoal(GOAL_ID, USER_ID);
    }

    @Test
    @DisplayName("DELETE /goals/{id}: deve retornar 404 quando a meta não for encontrada")
    void delete_shouldReturnNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Meta não encontrada"))
                .when(goalService).deleteGoal(99L, USER_ID);

        mockMvc.perform(delete(URL + "/99").with(user1()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /api/v1/goals/{id}/contributions ----------

    @Test
    @DisplayName("GET /goals/{id}/contributions: deve retornar a lista de contribuições")
    void listContributions_shouldReturnContributions() throws Exception {
        when(goalService.listContributions(GOAL_ID, USER_ID)).thenReturn(List.of(
                new GoalContributionResponse(CONTRIBUTION_ID, new BigDecimal("150.50"), LocalDate.of(2026, 3, 10), "Bônus"),
                new GoalContributionResponse(101L, new BigDecimal("-20.00"), LocalDate.of(2026, 3, 1), null)));

        mockMvc.perform(get(URL + "/" + GOAL_ID + "/contributions").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(CONTRIBUTION_ID))
                .andExpect(jsonPath("$[0].amount").value(150.5))
                .andExpect(jsonPath("$[0].date").value("2026-03-10"))
                .andExpect(jsonPath("$[0].notes").value("Bônus"))
                .andExpect(jsonPath("$[1].amount").value(-20.0));

        verify(goalService).listContributions(GOAL_ID, USER_ID);
    }

    @Test
    @DisplayName("GET /goals/{id}/contributions: deve retornar lista vazia")
    void listContributions_shouldReturnEmptyList() throws Exception {
        when(goalService.listContributions(GOAL_ID, USER_ID)).thenReturn(List.of());

        mockMvc.perform(get(URL + "/" + GOAL_ID + "/contributions").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /goals/{id}/contributions: deve retornar 404 quando a meta não for encontrada")
    void listContributions_shouldReturnNotFound() throws Exception {
        when(goalService.listContributions(99L, USER_ID))
                .thenThrow(new ResourceNotFoundException("Meta não encontrada"));

        mockMvc.perform(get(URL + "/99/contributions").with(user1()))
                .andExpect(status().isNotFound());
    }

    // ---------- POST /api/v1/goals/{id}/contributions ----------

    @Test
    @DisplayName("POST /goals/{id}/contributions: deve retornar 201 com a meta atualizada")
    void addContribution_shouldReturnCreated() throws Exception {
        when(goalService.addContribution(eq(GOAL_ID), eq(USER_ID), any(GoalContributionRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post(URL + "/" + GOAL_ID + "/contributions").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONTRIBUTION_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(GOAL_ID))
                .andExpect(jsonPath("$.currentAmount").value(1000.0));

        ArgumentCaptor<GoalContributionRequest> captor = ArgumentCaptor.forClass(GoalContributionRequest.class);
        verify(goalService).addContribution(eq(GOAL_ID), eq(USER_ID), captor.capture());
        GoalContributionRequest sent = captor.getValue();
        assertThat(sent.amount()).isEqualByComparingTo("150.50");
        assertThat(sent.date()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(sent.notes()).isEqualTo("Bônus");
    }

    @Test
    @DisplayName("POST /goals/{id}/contributions: deve aceitar valor negativo (retirada) e observação opcional")
    void addContribution_shouldAcceptNegativeAmountAndNoNotes() throws Exception {
        when(goalService.addContribution(eq(GOAL_ID), eq(USER_ID), any(GoalContributionRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post(URL + "/" + GOAL_ID + "/contributions").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":-30.00,"date":"2026-03-10"}"""))
                .andExpect(status().isCreated());

        ArgumentCaptor<GoalContributionRequest> captor = ArgumentCaptor.forClass(GoalContributionRequest.class);
        verify(goalService).addContribution(eq(GOAL_ID), eq(USER_ID), captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("-30.00");
        assertThat(captor.getValue().notes()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidContributionBodies")
    @DisplayName("POST /goals/{id}/contributions: deve retornar 400 para body inválido")
    void addContribution_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(URL + "/" + GOAL_ID + "/contributions").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    @Test
    @DisplayName("POST /goals/{id}/contributions: deve retornar 404 quando a meta não for encontrada")
    void addContribution_shouldReturnNotFound() throws Exception {
        when(goalService.addContribution(eq(99L), eq(USER_ID), any(GoalContributionRequest.class)))
                .thenThrow(new ResourceNotFoundException("Meta não encontrada"));

        mockMvc.perform(post(URL + "/99/contributions").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONTRIBUTION_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /goals/{id}/contributions: deve retornar erro 4xx quando uma regra de negócio for violada")
    void addContribution_shouldReturnClientError_whenBusinessRuleFails() throws Exception {
        when(goalService.addContribution(eq(GOAL_ID), eq(USER_ID), any(GoalContributionRequest.class)))
                .thenThrow(new BusinessRuleException("A retirada não pode deixar o saldo da meta negativo"));

        mockMvc.perform(post(URL + "/" + GOAL_ID + "/contributions").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONTRIBUTION_BODY))
                .andExpect(status().is4xxClientError());
    }

    // ---------- DELETE /api/v1/goals/{id}/contributions/{contributionId} ----------

    @Test
    @DisplayName("DELETE /goals/{id}/contributions/{contributionId}: deve retornar 204 e usar o userId do JWT")
    void deleteContribution_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete(URL + "/" + GOAL_ID + "/contributions/" + CONTRIBUTION_ID)
                        .with(user1()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(goalService).deleteContribution(GOAL_ID, CONTRIBUTION_ID, USER_ID);
    }

    @Test
    @DisplayName("DELETE /goals/{id}/contributions/{contributionId}: deve retornar 404 quando não encontrada")
    void deleteContribution_shouldReturnNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Contribuição não encontrada"))
                .when(goalService).deleteContribution(GOAL_ID, 999L, USER_ID);

        mockMvc.perform(delete(URL + "/" + GOAL_ID + "/contributions/999").with(user1()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /goals/{id}/contributions/{contributionId}: deve retornar erro 4xx quando uma regra de negócio for violada")
    void deleteContribution_shouldReturnClientError_whenBusinessRuleFails() throws Exception {
        doThrow(new BusinessRuleException("Contribuição não pertence a esta meta"))
                .when(goalService).deleteContribution(GOAL_ID, CONTRIBUTION_ID, USER_ID);

        mockMvc.perform(delete(URL + "/" + GOAL_ID + "/contributions/" + CONTRIBUTION_ID)
                        .with(user1()).with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("DELETE /goals/{id}/contributions/{contributionId}: deve retornar 400 quando um id não é numérico")
    void deleteContribution_shouldReturnBadRequest_whenIdIsNotNumeric() throws Exception {
        mockMvc.perform(delete(URL + "/" + GOAL_ID + "/contributions/abc").with(user1()).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    // ---------- autenticação ----------

    @Test
    @DisplayName("Todos os endpoints: devem retornar 401 sem autenticação")
    void allEndpoints_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        List<RequestBuilder> requests = List.of(
                get(URL),
                get(URL + "/" + GOAL_ID),
                post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_GOAL_BODY),
                put(URL + "/" + GOAL_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GOAL_BODY),
                patch(URL + "/" + GOAL_ID + "/cancel").with(csrf()),
                delete(URL + "/" + GOAL_ID).with(csrf()),
                get(URL + "/" + GOAL_ID + "/contributions"),
                post(URL + "/" + GOAL_ID + "/contributions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CONTRIBUTION_BODY),
                delete(URL + "/" + GOAL_ID + "/contributions/" + CONTRIBUTION_ID).with(csrf()));

        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(goalService);
    }
}