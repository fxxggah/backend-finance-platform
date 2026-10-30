package com.finance.goal.controller;

import com.finance.common.enums.GoalStatus;
import com.finance.common.enums.GoalType;
import com.finance.goal.dto.request.GoalContributionRequest;
import com.finance.goal.dto.response.GoalContributionResponse;
import com.finance.goal.dto.request.GoalRequest;
import com.finance.goal.dto.response.GoalResponse;
import com.finance.goal.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    private Long getUserId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> listGoals(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) GoalType type,
            @RequestParam(required = false) GoalStatus status) {
        return ResponseEntity.ok(goalService.listGoals(getUserId(jwt), type, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> getGoal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return ResponseEntity.ok(goalService.getGoal(id, getUserId(jwt)));
    }

    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid GoalRequest request) {
        GoalResponse response = goalService.createGoal(getUserId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> updateGoal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid GoalRequest request) {
        return ResponseEntity.ok(goalService.updateGoal(id, getUserId(jwt), request));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelGoal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        goalService.cancelGoal(id, getUserId(jwt));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        goalService.deleteGoal(id, getUserId(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/contributions")
    public ResponseEntity<List<GoalContributionResponse>> listContributions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return ResponseEntity.ok(goalService.listContributions(id, getUserId(jwt)));
    }

    @PostMapping("/{id}/contributions")
    public ResponseEntity<GoalResponse> addContribution(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid GoalContributionRequest request) {
        GoalResponse response = goalService.addContribution(id, getUserId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ResponseEntity<Void> deleteContribution(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long contributionId) {
        goalService.deleteContribution(id, contributionId, getUserId(jwt));
        return ResponseEntity.noContent().build();
    }
}