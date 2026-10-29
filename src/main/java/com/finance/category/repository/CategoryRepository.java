package com.finance.category.repository;

import com.finance.category.entity.Category;
import com.finance.common.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    Optional<Category> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    Optional<Category> findByUserIdAndNameIgnoreCaseAndType(Long userId, String name, TransactionType type);

    List<Category> findAllByUserIdAndDeletedFalse(Long userId);

    List<Category> findAllByUserIdAndTypeAndDeletedFalse(Long userId, TransactionType type);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalse(Long userId, String name, TransactionType type);

    boolean existsByUserIdAndNameIgnoreCaseAndTypeAndDeletedFalseAndIdNot(Long userId, String name,
                                                                          TransactionType type, Long id);

    void deleteAllByUserId(Long userId);
}