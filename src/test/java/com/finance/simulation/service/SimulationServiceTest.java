package com.finance.simulation.service;

import com.finance.simulation.dto.request.DebtPayoffSimulationRequest;
import com.finance.simulation.dto.request.InvestmentSimulationRequest;
import com.finance.simulation.dto.response.AmortizationScheduleDTO;
import com.finance.simulation.dto.response.DebtPayoffSimulationResponse;
import com.finance.simulation.dto.response.InvestmentMonthlyBreakdownDTO;
import com.finance.simulation.dto.response.InvestmentSimulationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SimulationServiceTest {

    private static final String DISCLAIMER = "Simulação ilustrativa, não é recomendação financeira.";
    private static final String INSUFFICIENT_PAYMENT = "O pagamento mensal é insuficiente para cobrir os juros da dívida.";

    private final SimulationService simulationService = new SimulationService();

    // ---------- helpers ----------

    private InvestmentSimulationRequest investment(String initial, String monthly, String annualRate, int years) {
        return new InvestmentSimulationRequest(
                new BigDecimal(initial), new BigDecimal(monthly), new BigDecimal(annualRate), years);
    }

    private DebtPayoffSimulationRequest debt(String amount, String monthlyRate, String monthlyPayment) {
        return new DebtPayoffSimulationRequest(
                new BigDecimal(amount), new BigDecimal(monthlyRate), new BigDecimal(monthlyPayment));
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------- simulateInvestment ----------

    @Test
    @DisplayName("simulateInvestment: com taxa zero não deve gerar juros e o saldo é só o valor aportado")
    void simulateInvestment_withZeroRate_shouldNotGenerateInterest() {
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("1000.00", "100.00", "0", 1));

        assertThat(response.totalInvested()).isEqualByComparingTo("2200.00");
        assertThat(response.totalInterest()).isEqualByComparingTo("0.00");
        assertThat(response.finalAmount()).isEqualByComparingTo("2200.00");
        assertThat(response.monthlyBreakdown()).hasSize(12);
        assertThat(response.monthlyBreakdown()).allSatisfy(
                month -> assertThat(month.interestEarned()).isEqualByComparingTo("0.00"));

        InvestmentMonthlyBreakdownDTO first = response.monthlyBreakdown().get(0);
        assertThat(first.month()).isEqualTo(1);
        assertThat(first.currentInvested()).isEqualByComparingTo("1100.00");
        assertThat(first.totalBalance()).isEqualByComparingTo("1100.00");

        InvestmentMonthlyBreakdownDTO last = response.monthlyBreakdown().get(11);
        assertThat(last.month()).isEqualTo(12);
        assertThat(last.currentInvested()).isEqualByComparingTo("2200.00");
        assertThat(last.totalBalance()).isEqualByComparingTo("2200.00");
    }

    @Test
    @DisplayName("simulateInvestment: todos os valores zerados devem resultar em saldo zero")
    void simulateInvestment_withAllZeroValues_shouldReturnZeroBalance() {
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("0", "0", "0", 1));

        assertThat(response.totalInvested()).isEqualByComparingTo("0.00");
        assertThat(response.totalInterest()).isEqualByComparingTo("0.00");
        assertThat(response.finalAmount()).isEqualByComparingTo("0.00");
        assertThat(response.monthlyBreakdown()).hasSize(12);
    }

    @Test
    @DisplayName("simulateInvestment: deve calcular o primeiro mês com a taxa mensal composta equivalente")
    void simulateInvestment_shouldComputeFirstMonthWithCompoundMonthlyRate() {
        // 12% a.a. => i = 1,12^(1/12) - 1 ≈ 0,94888% a.m. => 1000 * i = 9,49
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("1000.00", "100.00", "12", 1));

        InvestmentMonthlyBreakdownDTO first = response.monthlyBreakdown().get(0);
        assertThat(first.month()).isEqualTo(1);
        assertThat(first.currentInvested()).isEqualByComparingTo("1100.00");
        assertThat(first.interestEarned()).isEqualByComparingTo("9.49");
        assertThat(first.totalBalance()).isEqualByComparingTo("1109.49");
    }

    @Test
    @DisplayName("simulateInvestment: taxa máxima de 100% a.a. deve usar 2^(1/12) - 1 como taxa mensal")
    void simulateInvestment_withMaxRate_shouldComputeFirstMonth() {
        // 2^(1/12) - 1 ≈ 5,9463% a.m. => 1000 * i = 59,46
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("1000.00", "0.00", "100", 1));

        InvestmentMonthlyBreakdownDTO first = response.monthlyBreakdown().get(0);
        assertThat(first.interestEarned()).isEqualByComparingTo("59.46");
        assertThat(first.totalBalance()).isEqualByComparingTo("1059.46");
    }

    @Test
    @DisplayName("simulateInvestment: sem aportes, após 1 ano o saldo deve ser aproximadamente o valor inicial mais a taxa anual")
    void simulateInvestment_withoutContributions_shouldMatchAnnualRateAfterOneYear() {
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("1000.00", "0.00", "12", 1));

        // tolerância para o arredondamento mensal a 2 casas
        assertThat(response.finalAmount()).isCloseTo(new BigDecimal("1120.00"), within(new BigDecimal("0.10")));
        assertThat(response.totalInvested()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("simulateInvestment: sem valor inicial o primeiro mês não rende juros")
    void simulateInvestment_withoutInitialAmount_shouldNotEarnInterestInFirstMonth() {
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("0", "100.00", "12", 1));

        InvestmentMonthlyBreakdownDTO first = response.monthlyBreakdown().get(0);
        assertThat(first.interestEarned()).isEqualByComparingTo("0.00");
        assertThat(first.currentInvested()).isEqualByComparingTo("100.00");
        assertThat(first.totalBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("simulateInvestment: totais devem ser consistentes com o detalhamento mensal")
    void simulateInvestment_totalsShouldBeConsistentWithBreakdown() {
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("5000.00", "300.00", "10", 5));

        List<InvestmentMonthlyBreakdownDTO> breakdown = response.monthlyBreakdown();
        InvestmentMonthlyBreakdownDTO last = breakdown.get(breakdown.size() - 1);

        assertThat(breakdown).hasSize(60);
        assertThat(response.totalInvested()).isEqualByComparingTo("23000.00"); // 5000 + 60 * 300
        assertThat(last.currentInvested()).isEqualByComparingTo(response.totalInvested());
        assertThat(last.totalBalance()).isEqualByComparingTo(response.finalAmount());
        assertThat(response.finalAmount().subtract(response.totalInvested()))
                .isEqualByComparingTo(response.totalInterest());
        assertThat(sum(breakdown.stream().map(InvestmentMonthlyBreakdownDTO::interestEarned).toList()))
                .isEqualByComparingTo(response.totalInterest());
        assertThat(response.totalInterest()).isPositive();
    }

    @Test
    @DisplayName("simulateInvestment: o saldo deve crescer a cada mês quando há taxa positiva")
    void simulateInvestment_balanceShouldGrowEveryMonth() {
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("1000.00", "100.00", "10", 10));

        List<InvestmentMonthlyBreakdownDTO> breakdown = response.monthlyBreakdown();
        for (int i = 1; i < breakdown.size(); i++) {
            assertThat(breakdown.get(i).totalBalance()).isGreaterThan(breakdown.get(i - 1).totalBalance());
            assertThat(breakdown.get(i).currentInvested())
                    .isEqualByComparingTo(breakdown.get(i - 1).currentInvested().add(new BigDecimal("100.00")));
        }
    }

    @Test
    @DisplayName("simulateInvestment: deve numerar os meses de 1 até anos * 12")
    void simulateInvestment_shouldNumberMonthsSequentially() {
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("1000.00", "100.00", "8", 2));

        assertThat(response.monthlyBreakdown())
                .extracting(InvestmentMonthlyBreakdownDTO::month)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 24).boxed().toList());
    }

    @Test
    @DisplayName("simulateInvestment: prazo máximo de 50 anos deve gerar 600 meses")
    void simulateInvestment_withMaxYears_shouldGenerate600Months() {
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("1000.00", "100.00", "8", 50));

        assertThat(response.monthlyBreakdown()).hasSize(600);
        assertThat(response.monthlyBreakdown().get(599).month()).isEqualTo(600);
    }

    @Test
    @DisplayName("simulateInvestment: deve incluir o aviso de simulação ilustrativa")
    void simulateInvestment_shouldIncludeDisclaimer() {
        InvestmentSimulationResponse response = simulationService.simulateInvestment(
                investment("1000.00", "100.00", "8", 1));

        assertThat(response.disclaimer()).isEqualTo(DISCLAIMER);
    }

    // ---------- simulateDebtPayoff ----------

    @Test
    @DisplayName("simulateDebtPayoff: dívida sem juros deve ser quitada em parcelas fixas")
    void simulateDebtPayoff_withZeroRate_shouldPayInFixedInstallments() {
        DebtPayoffSimulationResponse response = simulationService.simulateDebtPayoff(
                debt("1000.00", "0", "500.00"));

        assertThat(response.months()).isEqualTo(2);
        assertThat(response.totalPaid()).isEqualByComparingTo("1000.00");
        assertThat(response.totalInterest()).isEqualByComparingTo("0.00");
        assertThat(response.schedule()).hasSize(2);

        AmortizationScheduleDTO first = response.schedule().get(0);
        assertThat(first.month()).isEqualTo(1);
        assertThat(first.paymentAmount()).isEqualByComparingTo("500.00");
        assertThat(first.principalPaid()).isEqualByComparingTo("500.00");
        assertThat(first.interestPaid()).isEqualByComparingTo("0.00");
        assertThat(first.remainingBalance()).isEqualByComparingTo("500.00");

        AmortizationScheduleDTO second = response.schedule().get(1);
        assertThat(second.month()).isEqualTo(2);
        assertThat(second.paymentAmount()).isEqualByComparingTo("500.00");
        assertThat(second.remainingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("simulateDebtPayoff: deve calcular juros sobre o saldo devedor e ajustar a última parcela")
    void simulateDebtPayoff_shouldComputeInterestAndAdjustLastInstallment() {
        // 1000 a 10% a.m., parcela de 600:
        // mês 1: juros 100,00 / principal 500,00 / saldo 500,00
        // mês 2: juros  50,00 / parcela final 550,00 / saldo 0,00
        DebtPayoffSimulationResponse response = simulationService.simulateDebtPayoff(
                debt("1000.00", "10", "600.00"));

        assertThat(response.months()).isEqualTo(2);
        assertThat(response.totalPaid()).isEqualByComparingTo("1150.00");
        assertThat(response.totalInterest()).isEqualByComparingTo("150.00");

        AmortizationScheduleDTO first = response.schedule().get(0);
        assertThat(first.paymentAmount()).isEqualByComparingTo("600.00");
        assertThat(first.interestPaid()).isEqualByComparingTo("100.00");
        assertThat(first.principalPaid()).isEqualByComparingTo("500.00");
        assertThat(first.remainingBalance()).isEqualByComparingTo("500.00");

        AmortizationScheduleDTO last = response.schedule().get(1);
        assertThat(last.paymentAmount()).isEqualByComparingTo("550.00");
        assertThat(last.interestPaid()).isEqualByComparingTo("50.00");
        assertThat(last.principalPaid()).isEqualByComparingTo("500.00");
        assertThat(last.remainingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("simulateDebtPayoff: última parcela menor que a parcela cheia quando a dívida não divide exato")
    void simulateDebtPayoff_withZeroRate_shouldAdjustLastPayment() {
        DebtPayoffSimulationResponse response = simulationService.simulateDebtPayoff(
                debt("100.00", "0", "30.00"));

        assertThat(response.months()).isEqualTo(4);
        assertThat(response.schedule())
                .extracting(AmortizationScheduleDTO::paymentAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("30.00"), new BigDecimal("30.00"),
                        new BigDecimal("30.00"), new BigDecimal("10.00"));
        assertThat(response.schedule())
                .extracting(AmortizationScheduleDTO::remainingBalance)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("70.00"), new BigDecimal("40.00"),
                        new BigDecimal("10.00"), new BigDecimal("0.00"));
        assertThat(response.totalPaid()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("simulateDebtPayoff: parcela maior que a dívida deve quitar em um mês pagando só o saldo")
    void simulateDebtPayoff_whenPaymentExceedsDebt_shouldPayOffInOneMonth() {
        DebtPayoffSimulationResponse response = simulationService.simulateDebtPayoff(
                debt("100.00", "0", "500.00"));

        assertThat(response.months()).isEqualTo(1);
        assertThat(response.schedule()).hasSize(1);
        assertThat(response.schedule().get(0).paymentAmount()).isEqualByComparingTo("100.00");
        assertThat(response.schedule().get(0).remainingBalance()).isEqualByComparingTo("0.00");
        assertThat(response.totalPaid()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("simulateDebtPayoff: totais devem ser consistentes com o cronograma e o saldo final deve ser zero")
    void simulateDebtPayoff_totalsShouldBeConsistentWithSchedule() {
        DebtPayoffSimulationResponse response = simulationService.simulateDebtPayoff(
                debt("5000.00", "2.5", "500.00"));

        List<AmortizationScheduleDTO> schedule = response.schedule();
        AmortizationScheduleDTO last = schedule.get(schedule.size() - 1);

        assertThat(response.months()).isEqualTo(schedule.size());
        assertThat(last.remainingBalance()).isEqualByComparingTo("0.00");
        assertThat(sum(schedule.stream().map(AmortizationScheduleDTO::paymentAmount).toList()))
                .isEqualByComparingTo(response.totalPaid());
        assertThat(sum(schedule.stream().map(AmortizationScheduleDTO::interestPaid).toList()))
                .isEqualByComparingTo(response.totalInterest());
        assertThat(sum(schedule.stream().map(AmortizationScheduleDTO::principalPaid).toList()))
                .isEqualByComparingTo("5000.00");
        assertThat(response.totalPaid().subtract(response.totalInterest()))
                .isEqualByComparingTo("5000.00");
        assertThat(response.totalInterest()).isPositive();
    }

    @Test
    @DisplayName("simulateDebtPayoff: o saldo devedor deve diminuir a cada mês")
    void simulateDebtPayoff_remainingBalanceShouldDecreaseEveryMonth() {
        DebtPayoffSimulationResponse response = simulationService.simulateDebtPayoff(
                debt("5000.00", "2.5", "500.00"));

        List<AmortizationScheduleDTO> schedule = response.schedule();
        for (int i = 1; i < schedule.size(); i++) {
            assertThat(schedule.get(i).remainingBalance()).isLessThan(schedule.get(i - 1).remainingBalance());
            assertThat(schedule.get(i).month()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("simulateDebtPayoff: pagamento menor que os juros do primeiro mês deve lançar 422")
    void simulateDebtPayoff_shouldThrowUnprocessable_whenPaymentIsBelowInterest() {
        assertThatThrownBy(() -> simulationService.simulateDebtPayoff(debt("1000.00", "10", "50.00")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(INSUFFICIENT_PAYMENT);
                });
    }

    @Test
    @DisplayName("simulateDebtPayoff: pagamento exatamente igual aos juros também deve lançar 422")
    void simulateDebtPayoff_shouldThrowUnprocessable_whenPaymentEqualsInterest() {
        assertThatThrownBy(() -> simulationService.simulateDebtPayoff(debt("1000.00", "10", "100.00")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(INSUFFICIENT_PAYMENT);
                });
    }

    @Test
    @DisplayName("simulateDebtPayoff: com taxa zero a regra de juros insuficientes não se aplica")
    void simulateDebtPayoff_withZeroRate_shouldNotThrowForSmallPayment() {
        assertThatCode(() -> simulationService.simulateDebtPayoff(debt("100.00", "0", "0.01")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("simulateDebtPayoff: deve limitar a simulação a 600 meses quando a dívida não é quitada")
    void simulateDebtPayoff_shouldStopAtMaxMonths_whenDebtIsNotPaidOff() {
        // juros do 1º mês = 1000,00; parcela de 1000,01 amortiza só 0,01 por mês no início
        DebtPayoffSimulationResponse response = simulationService.simulateDebtPayoff(
                debt("100000.00", "1", "1000.01"));

        assertThat(response.months()).isEqualTo(600);
        assertThat(response.schedule()).hasSize(600);
        assertThat(response.schedule().get(0).principalPaid()).isEqualByComparingTo("0.01");
        assertThat(response.schedule().get(599).remainingBalance()).isPositive();
    }

    @Test
    @DisplayName("simulateDebtPayoff: deve incluir o aviso de simulação ilustrativa")
    void simulateDebtPayoff_shouldIncludeDisclaimer() {
        DebtPayoffSimulationResponse response = simulationService.simulateDebtPayoff(
                debt("1000.00", "0", "500.00"));

        assertThat(response.disclaimer()).isEqualTo(DISCLAIMER);
    }
}