package com.finance.dashboard.controller;

import com.finance.common.enums.Period;
import com.finance.dashboard.dto.CategoryExpenseDTO;
import com.finance.dashboard.dto.DashboardSummaryDTO;
import com.finance.dashboard.dto.EvolutionDTO;
import com.finance.dashboard.dto.TransactionRecentDTO;
import com.finance.dashboard.service.DashboardService;
import com.finance.transaction.entity.Transaction;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    private Long extractUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDTO> getSummary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "MONTH") Period period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long creditCardId) {

        return ResponseEntity.ok(dashboardService.getSummary(extractUserId(jwt), period, date, from, to, accountId, creditCardId));
    }

    @GetMapping("/expenses-by-category")
    public ResponseEntity<List<CategoryExpenseDTO>> getExpensesByCategory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "MONTH") Period period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long creditCardId) {

        return ResponseEntity.ok(dashboardService.getExpensesByCategory(extractUserId(jwt), period, date, from, to, accountId, creditCardId));
    }

    @GetMapping("/evolution")
    public ResponseEntity<EvolutionDTO> getEvolution(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "MONTH") Period period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long creditCardId) {

        return ResponseEntity.ok(dashboardService.getEvolution(extractUserId(jwt), period, date, from, to, accountId, creditCardId));
    }

    @GetMapping("/recent-transactions")
    public ResponseEntity<List<TransactionRecentDTO>> getRecentTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentTransactions(extractUserId(jwt), limit));
    }
}