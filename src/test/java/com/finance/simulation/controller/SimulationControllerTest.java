package com.finance.simulation.controller;

import com.finance.simulation.dto.request.DebtPayoffSimulationRequest;
import com.finance.simulation.dto.request.InvestmentSimulationRequest;
import com.finance.simulation.dto.response.AmortizationScheduleDTO;
import com.finance.simulation.dto.response.DebtPayoffSimulationResponse;
import com.finance.simulation.dto.response.InvestmentMonthlyBreakdownDTO;
import com.finance.simulation.dto.response.InvestmentSimulationResponse;
import com.finance.simulation.service.SimulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulationController.class)
class SimulationControllerTest {

    private static final String INVESTMENT_URL = "/api/v1/simulations/investment";
    private static final String DEBT_URL = "/api/v1/simulations/debt-payoff";
    private static final Long USER_ID = 1L;
    private static final String DISCLAIMER = "Simulação ilustrativa, não é recomendação financeira.";

    private static final String VALID_INVESTMENT_BODY = """
            {"initialAmount":1000.00,"monthlyContribution":100.00,"annualRate":12.0,"years":10}""";

    private static final String VALID_DEBT_BODY = """
            {"debtAmount":1000.00,"monthlyRate":10.0,"monthlyPayment":600.00}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationService simulationService;

    // O userId vem do subject do JWT
    private static RequestPostProcessor user1() {
        return jwt().jwt(j -> j.subject(String.valueOf(USER_ID)));
    }

    private static InvestmentSimulationResponse sampleInvestmentResponse() {
        return new InvestmentSimulationResponse(
                new BigDecimal("1200.00"),
                new BigDecimal("20.02"),
                new BigDecimal("1220.02"),
                List.of(
                        new InvestmentMonthlyBreakdownDTO(1, new BigDecimal("1100.00"),
                                new BigDecimal("9.49"), new BigDecimal("1109.49")),
                        new InvestmentMonthlyBreakdownDTO(2, new BigDecimal("1200.00"),
                                new BigDecimal("10.53"), new BigDecimal("1220.02"))));
    }

    private static DebtPayoffSimulationResponse sampleDebtResponse() {
        return new DebtPayoffSimulationResponse(
                2,
                new BigDecimal("1150.00"),
                new BigDecimal("150.00"),
                List.of(
                        new AmortizationScheduleDTO(1, new BigDecimal("600.00"), new BigDecimal("500.00"),
                                new BigDecimal("100.00"), new BigDecimal("500.00")),
                        new AmortizationScheduleDTO(2, new BigDecimal("550.00"), new BigDecimal("500.00"),
                                new BigDecimal("50.00"), new BigDecimal("0.00"))));
    }

    static Stream<Arguments> invalidInvestmentBodies() {
        return Stream.of(
                Arguments.of("body vazio", "{}"),
                Arguments.of("valor inicial ausente", """
                        {"monthlyContribution":100,"annualRate":12,"years":10}"""),
                Arguments.of("valor inicial negativo", """
                        {"initialAmount":-1,"monthlyContribution":100,"annualRate":12,"years":10}"""),
                Arguments.of("aporte mensal ausente", """
                        {"initialAmount":1000,"annualRate":12,"years":10}"""),
                Arguments.of("aporte mensal negativo", """
                        {"initialAmount":1000,"monthlyContribution":-1,"annualRate":12,"years":10}"""),
                Arguments.of("taxa anual ausente", """
                        {"initialAmount":1000,"monthlyContribution":100,"years":10}"""),
                Arguments.of("taxa anual negativa", """
                        {"initialAmount":1000,"monthlyContribution":100,"annualRate":-0.1,"years":10}"""),
                Arguments.of("taxa anual acima de 100", """
                        {"initialAmount":1000,"monthlyContribution":100,"annualRate":100.01,"years":10}"""),
                Arguments.of("taxa anual não numérica", """
                        {"initialAmount":1000,"monthlyContribution":100,"annualRate":"abc","years":10}"""),
                Arguments.of("prazo ausente", """
                        {"initialAmount":1000,"monthlyContribution":100,"annualRate":12}"""),
                Arguments.of("prazo zero", """
                        {"initialAmount":1000,"monthlyContribution":100,"annualRate":12,"years":0}"""),
                Arguments.of("prazo acima de 50 anos", """
                        {"initialAmount":1000,"monthlyContribution":100,"annualRate":12,"years":51}"""));
    }

    static Stream<Arguments> validInvestmentBoundaryBodies() {
        return Stream.of(
                Arguments.of("valores mínimos (tudo zero e 1 ano)", """
                        {"initialAmount":0,"monthlyContribution":0,"annualRate":0,"years":1}"""),
                Arguments.of("taxa máxima de 100%", """
                        {"initialAmount":1000,"monthlyContribution":100,"annualRate":100,"years":10}"""),
                Arguments.of("prazo máximo de 50 anos", """
                        {"initialAmount":1000,"monthlyContribution":100,"annualRate":12,"years":50}"""));
    }

    static Stream<Arguments> invalidDebtBodies() {
        return Stream.of(
                Arguments.of("body vazio", "{}"),
                Arguments.of("valor da dívida ausente", """
                        {"monthlyRate":10,"monthlyPayment":600}"""),
                Arguments.of("valor da dívida zero", """
                        {"debtAmount":0,"monthlyRate":10,"monthlyPayment":600}"""),
                Arguments.of("valor da dívida negativo", """
                        {"debtAmount":-100,"monthlyRate":10,"monthlyPayment":600}"""),
                Arguments.of("taxa mensal ausente", """
                        {"debtAmount":1000,"monthlyPayment":600}"""),
                Arguments.of("taxa mensal negativa", """
                        {"debtAmount":1000,"monthlyRate":-1,"monthlyPayment":600}"""),
                Arguments.of("taxa mensal não numérica", """
                        {"debtAmount":1000,"monthlyRate":"abc","monthlyPayment":600}"""),
                Arguments.of("parcela mensal ausente", """
                        {"debtAmount":1000,"monthlyRate":10}"""),
                Arguments.of("parcela mensal zero", """
                        {"debtAmount":1000,"monthlyRate":10,"monthlyPayment":0}"""),
                Arguments.of("parcela mensal negativa", """
                        {"debtAmount":1000,"monthlyRate":10,"monthlyPayment":-50}"""));
    }

    // ---------- POST /api/v1/simulations/investment ----------

    @Test
    @DisplayName("POST /simulations/investment: deve retornar 200 com o resultado da simulação")
    void simulateInvestment_shouldReturnOk() throws Exception {
        when(simulationService.simulateInvestment(any(InvestmentSimulationRequest.class)))
                .thenReturn(sampleInvestmentResponse());

        mockMvc.perform(post(INVESTMENT_URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_INVESTMENT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvested").value(1200.00))
                .andExpect(jsonPath("$.totalInterest").value(20.02))
                .andExpect(jsonPath("$.finalAmount").value(1220.02))
                .andExpect(jsonPath("$.monthlyBreakdown.length()").value(2))
                .andExpect(jsonPath("$.monthlyBreakdown[0].month").value(1))
                .andExpect(jsonPath("$.monthlyBreakdown[0].currentInvested").value(1100.00))
                .andExpect(jsonPath("$.monthlyBreakdown[0].interestEarned").value(9.49))
                .andExpect(jsonPath("$.monthlyBreakdown[0].totalBalance").value(1109.49))
                .andExpect(jsonPath("$.monthlyBreakdown[1].month").value(2))
                .andExpect(jsonPath("$.disclaimer").value(DISCLAIMER));
    }

    @Test
    @DisplayName("POST /simulations/investment: deve repassar os campos do body ao service")
    void simulateInvestment_shouldPassRequestToService() throws Exception {
        when(simulationService.simulateInvestment(any(InvestmentSimulationRequest.class)))
                .thenReturn(sampleInvestmentResponse());

        mockMvc.perform(post(INVESTMENT_URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_INVESTMENT_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<InvestmentSimulationRequest> captor = ArgumentCaptor.forClass(InvestmentSimulationRequest.class);
        verify(simulationService).simulateInvestment(captor.capture());
        InvestmentSimulationRequest sent = captor.getValue();
        assertThat(sent.initialAmount()).isEqualByComparingTo("1000.00");
        assertThat(sent.monthlyContribution()).isEqualByComparingTo("100.00");
        assertThat(sent.annualRate()).isEqualByComparingTo("12.0");
        assertThat(sent.years()).isEqualTo(10);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validInvestmentBoundaryBodies")
    @DisplayName("POST /simulations/investment: deve aceitar valores nos limites permitidos")
    void simulateInvestment_shouldAcceptBoundaryValues(String scenario, String body) throws Exception {
        when(simulationService.simulateInvestment(any(InvestmentSimulationRequest.class)))
                .thenReturn(sampleInvestmentResponse());

        mockMvc.perform(post(INVESTMENT_URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInvestmentBodies")
    @DisplayName("POST /simulations/investment: deve retornar 400 para body inválido")
    void simulateInvestment_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(INVESTMENT_URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(simulationService);
    }

    @Test
    @DisplayName("POST /simulations/investment: deve retornar 400 quando o body não é um JSON válido")
    void simulateInvestment_shouldReturnBadRequest_whenBodyIsMalformed() throws Exception {
        mockMvc.perform(post(INVESTMENT_URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{initialAmount:"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(simulationService);
    }

    // ---------- POST /api/v1/simulations/debt-payoff ----------

    @Test
    @DisplayName("POST /simulations/debt-payoff: deve retornar 200 com o cronograma de amortização")
    void simulateDebtPayoff_shouldReturnOk() throws Exception {
        when(simulationService.simulateDebtPayoff(any(DebtPayoffSimulationRequest.class)))
                .thenReturn(sampleDebtResponse());

        mockMvc.perform(post(DEBT_URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DEBT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").value(2))
                .andExpect(jsonPath("$.totalPaid").value(1150.00))
                .andExpect(jsonPath("$.totalInterest").value(150.00))
                .andExpect(jsonPath("$.schedule.length()").value(2))
                .andExpect(jsonPath("$.schedule[0].month").value(1))
                .andExpect(jsonPath("$.schedule[0].paymentAmount").value(600.00))
                .andExpect(jsonPath("$.schedule[0].principalPaid").value(500.00))
                .andExpect(jsonPath("$.schedule[0].interestPaid").value(100.00))
                .andExpect(jsonPath("$.schedule[0].remainingBalance").value(500.00))
                .andExpect(jsonPath("$.schedule[1].remainingBalance").value(0.00))
                .andExpect(jsonPath("$.disclaimer").value(DISCLAIMER));
    }

    @Test
    @DisplayName("POST /simulations/debt-payoff: deve repassar os campos do body ao service")
    void simulateDebtPayoff_shouldPassRequestToService() throws Exception {
        when(simulationService.simulateDebtPayoff(any(DebtPayoffSimulationRequest.class)))
                .thenReturn(sampleDebtResponse());

        mockMvc.perform(post(DEBT_URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DEBT_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<DebtPayoffSimulationRequest> captor = ArgumentCaptor.forClass(DebtPayoffSimulationRequest.class);
        verify(simulationService).simulateDebtPayoff(captor.capture());
        DebtPayoffSimulationRequest sent = captor.getValue();
        assertThat(sent.debtAmount()).isEqualByComparingTo("1000.00");
        assertThat(sent.monthlyRate()).isEqualByComparingTo("10.0");
        assertThat(sent.monthlyPayment()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("POST /simulations/debt-payoff: deve aceitar taxa mensal zero")
    void simulateDebtPayoff_shouldAcceptZeroRate() throws Exception {
        when(simulationService.simulateDebtPayoff(any(DebtPayoffSimulationRequest.class)))
                .thenReturn(sampleDebtResponse());

        mockMvc.perform(post(DEBT_URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"debtAmount":1000,"monthlyRate":0,"monthlyPayment":500}"""))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDebtBodies")
    @DisplayName("POST /simulations/debt-payoff: deve retornar 400 para body inválido")
    void simulateDebtPayoff_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(DEBT_URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(simulationService);
    }

    @Test
    @DisplayName("POST /simulations/debt-payoff: deve retornar 422 quando o pagamento não cobre os juros")
    void simulateDebtPayoff_shouldReturnUnprocessableEntity_whenPaymentIsInsufficient() throws Exception {
        when(simulationService.simulateDebtPayoff(any(DebtPayoffSimulationRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "O pagamento mensal é insuficiente para cobrir os juros da dívida."));

        mockMvc.perform(post(DEBT_URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DEBT_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------- autenticação ----------

    @Test
    @DisplayName("Todos os endpoints: devem retornar 401 sem autenticação")
    void allEndpoints_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        List<RequestBuilder> requests = List.of(
                post(INVESTMENT_URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_INVESTMENT_BODY),
                post(DEBT_URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DEBT_BODY));

        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(simulationService);
    }
}