package com.finance.goal.service;

import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
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
import com.finance.goal.entity.Goal;
import com.finance.goal.entity.GoalContribution;
import com.finance.goal.repository.GoalContributionRepository;
import com.finance.goal.repository.GoalRepository;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals(Long userId, GoalType type, GoalStatus status) {
        List<Goal> goals;
        if (status != null) {
            goals = goalRepository.findAllByUserIdAndStatus(userId, status);
        } else {
            goals = goalRepository.findAllByUserId(userId);
        }

        if (type != null) {
            goals = goals.stream().filter(g -> g.getType() == type).toList();
        }

        return goals.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoal(Long id, Long userId) {
        Goal goal = findGoalOrThrow(id, userId);
        return toResponse(goal);
    }

    @Transactional
    public GoalResponse createGoal(Long userId, GoalRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        validateGoalRules(request);

        Goal goal = new Goal();
        goal.setUser(user);
        mapRequestToEntity(request, goal);

        if (goal.getType() == GoalType.SAVINGS) {
            goal.setCurrentAmount(BigDecimal.ZERO);
        }

        goal = goalRepository.save(goal);
        return toResponse(goal);
    }

    @Transactional
    public GoalResponse updateGoal(Long id, Long userId, GoalRequest request) {
        Goal goal = findGoalOrThrow(id, userId);
        validateGoalRules(request);
        mapRequestToEntity(request, goal);
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public void cancelGoal(Long id, Long userId) {
        Goal goal = findGoalOrThrow(id, userId);
        goal.setStatus(GoalStatus.CANCELLED);
        goalRepository.save(goal);
    }

    @Transactional
    public void deleteGoal(Long id, Long userId) {
        Goal goal = findGoalOrThrow(id, userId);
        goalRepository.delete(goal);
    }

    @Transactional(readOnly = true)
    public List<GoalContributionResponse> listContributions(Long goalId, Long userId) {
        findGoalOrThrow(goalId, userId); // Valida se a meta existe e pertence ao usuário
        return contributionRepository.findAllByGoalIdAndGoalUserIdOrderByDateDesc(goalId, userId)
                .stream()
                .map(c -> new GoalContributionResponse(c.getId(), c.getAmount(), c.getDate(), c.getNotes()))
                .toList();
    }

    @Transactional
    public GoalResponse addContribution(Long goalId, Long userId, GoalContributionRequest request) {
        Goal goal = findGoalOrThrow(goalId, userId);

        if (goal.getType() != GoalType.SAVINGS) {
            throw new BusinessRuleException("Contribuições só são permitidas para metas do tipo SAVINGS");
        }
        if (goal.getStatus() == GoalStatus.CANCELLED) {
            throw new BusinessRuleException("Não é possível contribuir para uma meta cancelada");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessRuleException("O valor da contribuição não pode ser zero");
        }

        BigDecimal newAmount = goal.getCurrentAmount().add(request.amount());
        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("A retirada não pode deixar o saldo da meta negativo");
        }

        GoalContribution contribution = new GoalContribution(goal, request.amount(), request.date(), request.notes());
        contributionRepository.save(contribution);

        goal.setCurrentAmount(newAmount);
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public void deleteContribution(Long goalId, Long contributionId, Long userId) {
        GoalContribution contribution = contributionRepository.findByIdAndGoalUserId(contributionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Contribuição não encontrada"));

        if (!contribution.getGoal().getId().equals(goalId)) {
            throw new BusinessRuleException("Contribuição não pertence a esta meta");
        }

        Goal goal = contribution.getGoal();
        BigDecimal newAmount = goal.getCurrentAmount().subtract(contribution.getAmount());

        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("A exclusão desta contribuição deixaria o saldo da meta negativo");
        }

        goal.setCurrentAmount(newAmount);
        goalRepository.save(goal);
        contributionRepository.delete(contribution);
    }

    // --- Métodos Auxiliares ---

    private Goal findGoalOrThrow(Long id, Long userId) {
        return goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada"));
    }

    private void validateGoalRules(GoalRequest request) {
        if (request.type() == GoalType.INCOME || request.type() == GoalType.EXPENSE) {
            if (request.startDate() == null || request.endDate() == null) {
                throw new BusinessRuleException("Metas de INCOME/EXPENSE exigem startDate e endDate");
            }
            if (request.endDate().isBefore(request.startDate())) {
                throw new BusinessRuleException("A data final não pode ser anterior à data inicial");
            }
        }
    }

    private void mapRequestToEntity(GoalRequest request, Goal goal) {
        goal.setType(request.type());
        goal.setName(request.name());
        goal.setDescription(request.description());
        goal.setIcon(request.icon() != null ? request.icon() : "target");
        goal.setColor(request.color());
        goal.setTargetAmount(request.targetAmount());
        goal.setStartDate(request.startDate());
        goal.setEndDate(request.endDate());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findByIdAndUserId(request.categoryId(), goal.getUser().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));

            // Valida se o tipo da categoria bate com o da meta (apenas visual, mas boa prática manter alinhado)
            if (goal.getType() == GoalType.INCOME && !category.getType().name().equals("INCOME")) {
                throw new BusinessRuleException("Meta de receita exige uma categoria de receita");
            }
            if (goal.getType() == GoalType.EXPENSE && !category.getType().name().equals("EXPENSE")) {
                throw new BusinessRuleException("Meta de despesa exige uma categoria de despesa");
            }
            goal.setCategory(category);
        } else {
            goal.setCategory(null);
        }
    }

    private GoalResponse toResponse(Goal goal) {
        BigDecimal currentAmount = goal.getCurrentAmount() != null ? goal.getCurrentAmount() : BigDecimal.ZERO;

        // TODO: Para INCOME e EXPENSE, o currentAmount virá da query de Dashboard.
        // Assumindo ZERO caso não estejamos injetando o cálculo agregado no V1.

        BigDecimal percentage = BigDecimal.ZERO;
        if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
            percentage = currentAmount.multiply(new BigDecimal("100"))
                    .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP);
        }

        GoalState state = calculateState(goal, percentage);
        BigDecimal monthlyNeeded = calculateMonthlyNeeded(goal, currentAmount);

        CategorySimpleDto catDto = null;
        if (goal.getCategory() != null) {
            catDto = new CategorySimpleDto(
                    goal.getCategory().getId(),
                    goal.getCategory().getName(),
                    goal.getCategory().getColor(),
                    goal.getCategory().getIcon()
            );
        }

        return new GoalResponse(
                goal.getId(), goal.getType(), goal.getName(), goal.getDescription(),
                goal.getIcon(), goal.getColor(), goal.getTargetAmount(), currentAmount,
                goal.getStartDate(), goal.getEndDate(), goal.getStatus(), state,
                percentage, monthlyNeeded, catDto
        );
    }

    private GoalState calculateState(Goal goal, BigDecimal percentage) {
        LocalDate today = LocalDate.now();
        boolean isPeriodEnded = goal.getEndDate() != null && today.isAfter(goal.getEndDate());

        if (goal.getType() == GoalType.EXPENSE) {
            if (percentage.compareTo(new BigDecimal("100")) > 0) return GoalState.EXCEEDED;
            if (isPeriodEnded && percentage.compareTo(new BigDecimal("100")) <= 0) return GoalState.ACHIEVED;
            if (percentage.compareTo(new BigDecimal("80")) >= 0) return GoalState.WARNING;
            return GoalState.IN_PROGRESS;
        } else {
            if (percentage.compareTo(new BigDecimal("100")) >= 0) return GoalState.ACHIEVED;
            if (isPeriodEnded) return GoalState.EXPIRED;
            return GoalState.IN_PROGRESS;
        }
    }

    private BigDecimal calculateMonthlyNeeded(Goal goal, BigDecimal currentAmount) {
        if (goal.getType() != GoalType.SAVINGS || goal.getEndDate() == null) {
            return null;
        }

        long monthsBetween = ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1), goal.getEndDate().withDayOfMonth(1));
        long monthsRemaining = Math.max(1, monthsBetween);

        BigDecimal remainingAmount = goal.getTargetAmount().subtract(currentAmount);
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return remainingAmount.divide(new BigDecimal(monthsRemaining), 2, RoundingMode.HALF_UP);
    }
}