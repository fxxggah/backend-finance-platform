package com.finance.simulation.controller;

import com.finance.simulation.dto.request.DebtPayoffSimulationRequest;
import com.finance.simulation.dto.request.InvestmentSimulationRequest;
import com.finance.simulation.dto.response.DebtPayoffSimulationResponse;
import com.finance.simulation.dto.response.InvestmentSimulationResponse;
import com.finance.simulation.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/investment")
    public ResponseEntity<InvestmentSimulationResponse> simulateInvestment(@RequestBody @Valid InvestmentSimulationRequest request) {
        // Conforme a regra de negócio do README.md, esse endpoint retorna 200 OK
        return ResponseEntity.ok(simulationService.simulateInvestment(request));
    }

    @PostMapping("/debt-payoff")
    public ResponseEntity<DebtPayoffSimulationResponse> simulateDebtPayoff(@RequestBody @Valid DebtPayoffSimulationRequest request) {
        return ResponseEntity.ok(simulationService.simulateDebtPayoff(request));
    }
}