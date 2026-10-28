package com.finance.goal.repository;

import com.finance.goal.entity.GoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

    Optional<GoalContribution> findByIdAndGoalUserId(Long id, Long userId);

    List<GoalContribution> findAllByGoalIdAndGoalUserIdOrderByDateDesc(Long goalId, Long userId);

    boolean existsByIdAndGoalUserId(Long id, Long userId);

    void deleteAllByGoalUserId(Long userId);
}
