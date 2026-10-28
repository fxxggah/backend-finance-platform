package com.finance.goal.repository;

import com.finance.common.enums.GoalStatus;
import com.finance.goal.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    List<Goal> findAllByUserId(Long userId);

    List<Goal> findAllByUserIdAndStatus(Long userId, GoalStatus status);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByCategoryId(Long categoryId);

    void deleteAllByUserId(Long userId);
}
