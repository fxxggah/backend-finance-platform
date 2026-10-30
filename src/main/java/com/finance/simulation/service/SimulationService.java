package com.finance.simulation.service;

import com.finance.simulation.dto.request.DebtPayoffSimulationRequest;
import com.finance.simulation.dto.request.InvestmentSimulationRequest;
import com.finance.simulation.dto.response.AmortizationScheduleDTO;
import com.finance.simulation.dto.response.DebtPayoffSimulationResponse;
import com.finance.simulation.dto.response.InvestmentMonthlyBreakdownDTO;
import com.finance.simulation.dto.response.InvestmentSimulationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class SimulationService {

    private static final int MAX_DEBT_MONTHS = 600;
    private static final MathContext MC = MathContext.DECIMAL64;

    public InvestmentSimulationResponse simulateInvestment(InvestmentSimulationRequest request) {
        int totalMonths = request.years() * 12;

        // Taxa mensal composta: i = (1 + taxaAnual)^(1/12) - 1
        double annualRateDecimal = request.annualRate().divide(BigDecimal.valueOf(100), MC).doubleValue();
        double monthlyRateDouble = Math.pow(1.0 + annualRateDecimal, 1.0 / 12.0) - 1.0;
        BigDecimal monthlyRate = BigDecimal.valueOf(monthlyRateDouble);

        BigDecimal currentBalance = request.initialAmount();
        BigDecimal totalInvested = request.initialAmount();

        List<InvestmentMonthlyBreakdownDTO> breakdown = new ArrayList<>(totalMonths);

        for (int month = 1; month <= totalMonths; month++) {
            BigDecimal interestEarned = currentBalance.multiply(monthlyRate, MC).setScale(2, RoundingMode.HALF_EVEN);
            currentBalance = currentBalance.add(interestEarned).add(request.monthlyContribution());
            totalInvested = totalInvested.add(request.monthlyContribution());

            breakdown.add(new InvestmentMonthlyBreakdownDTO(
                    month,
                    totalInvested.setScale(2, RoundingMode.HALF_EVEN),
                    interestEarned,
                    currentBalance.setScale(2, RoundingMode.HALF_EVEN)
            ));
        }

        BigDecimal totalInterest = currentBalance.subtract(totalInvested).setScale(2, RoundingMode.HALF_EVEN);

        return new InvestmentSimulationResponse(
                totalInvested.setScale(2, RoundingMode.HALF_EVEN),
                totalInterest,
                currentBalance.setScale(2, RoundingMode.HALF_EVEN),
                breakdown
        );
    }

    public DebtPayoffSimulationResponse simulateDebtPayoff(DebtPayoffSimulationRequest request) {
        BigDecimal monthlyRateDecimal = request.monthlyRate().divide(BigDecimal.valueOf(100), MC);

        // Validação da regra crítica: Se pagamento mensal <= juros gerados, dívida nunca é paga
        BigDecimal firstMonthInterest = request.debtAmount().multiply(monthlyRateDecimal, MC);
        if (request.monthlyPayment().compareTo(firstMonthInterest) <= 0 && request.monthlyRate().compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O pagamento mensal é insuficiente para cobrir os juros da dívida.");
        }

        BigDecimal remainingBalance = request.debtAmount();
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;

        List<AmortizationScheduleDTO> schedule = new ArrayList<>();
        int month = 0;

        while (remainingBalance.compareTo(BigDecimal.ZERO) > 0 && month < MAX_DEBT_MONTHS) {
            month++;
            BigDecimal interestPaid = remainingBalance.multiply(monthlyRateDecimal, MC).setScale(2, RoundingMode.HALF_EVEN);

            BigDecimal paymentAmount = request.monthlyPayment();
            BigDecimal balanceWithInterest = remainingBalance.add(interestPaid);

            // Ajusta o último pagamento se for menor que a parcela cheia
            if (balanceWithInterest.compareTo(paymentAmount) <= 0) {
                paymentAmount = balanceWithInterest.setScale(2, RoundingMode.HALF_EVEN);
            }

            BigDecimal principalPaid = paymentAmount.subtract(interestPaid).setScale(2, RoundingMode.HALF_EVEN);
            remainingBalance = remainingBalance.subtract(principalPaid).setScale(2, RoundingMode.HALF_EVEN);

            // Correção de precisão no final (evitar -0.01)
            if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) {
                remainingBalance = BigDecimal.ZERO;
            }

            totalPaid = totalPaid.add(paymentAmount);
            totalInterest = totalInterest.add(interestPaid);

            schedule.add(new AmortizationScheduleDTO(
                    month,
                    paymentAmount,
                    principalPaid,
                    interestPaid,
                    remainingBalance
            ));
        }

        return new DebtPayoffSimulationResponse(
                month,
                totalPaid.setScale(2, RoundingMode.HALF_EVEN),
                totalInterest.setScale(2, RoundingMode.HALF_EVEN),
                schedule
        );
    }
}