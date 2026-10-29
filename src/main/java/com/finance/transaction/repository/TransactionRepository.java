package com.finance.transaction.repository;

import com.finance.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    Optional<Transaction> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    Page<Transaction> findAllByUserIdAndDeletedFalse(Long userId, Pageable pageable);

    List<Transaction> findAllByInstallmentGroupIdAndUserId(String installmentGroupId, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByAccountIdAndDeletedFalse(Long accountId);

    boolean existsByCreditCardIdAndDeletedFalse(Long creditCardId);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    void deleteAllByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END), 0) " +
            "FROM Transaction t WHERE t.account.id = :accountId AND t.deleted = false")
    BigDecimal getNetBalanceByAccountId(@Param("accountId") Long accountId);
}
