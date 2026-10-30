package com.finance.transaction.repository;

import com.finance.common.enums.TransactionType;
import com.finance.dashboard.dto.CategoryExpenseDTO;
import com.finance.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.creditCard.id = :creditCardId AND t.deleted = false")
    BigDecimal sumTotalAmountByCreditCardId(@Param("creditCardId") Long creditCardId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.creditCard.id = :creditCardId AND t.invoiceReference = :invoiceReference AND t.deleted = false")
    BigDecimal sumAmountByCreditCardIdAndInvoiceReference(@Param("creditCardId") Long creditCardId, @Param("invoiceReference") LocalDate invoiceReference);

    @Query("SELECT DISTINCT t.invoiceReference FROM Transaction t WHERE t.creditCard.id = :creditCardId AND t.deleted = false ORDER BY t.invoiceReference DESC")
    List<LocalDate> findDistinctInvoiceReferencesByCreditCardId(@Param("creditCardId") Long creditCardId);

    List<Transaction> findAllByCreditCardIdAndInvoiceReferenceAndDeletedFalse(Long creditCardId, LocalDate invoiceReference);

    @Query("SELECT t FROM Transaction t " +
            "LEFT JOIN FETCH t.category " +
            "LEFT JOIN FETCH t.account " +
            "LEFT JOIN FETCH t.creditCard " +
            "WHERE t.user.id = :userId AND t.deleted = false")
    Page<Transaction> findAllByUserIdAndDeletedFalseWithFetch(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.deleted = false " +
            "AND t.type = :type AND t.date >= :startDate AND t.date <= :endDate " +
            "AND (:accountId IS NULL OR t.account.id = :accountId) " +
            "AND (:creditCardId IS NULL OR t.creditCard.id = :creditCardId)")
    BigDecimal sumAmountByTypeAndPeriodAndFilters(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("accountId") Long accountId,
            @Param("creditCardId") Long creditCardId
    );

    @Query("SELECT new com.finance.dashboard.dto.CategoryExpenseDTO(" +
            "c.id, c.name, c.color, c.icon, SUM(t.amount), CAST(0.0 AS bigdecimal), COUNT(t.id)) " +
            "FROM Transaction t JOIN t.category c " +
            "WHERE t.user.id = :userId AND t.deleted = false AND t.type = 'EXPENSE' " +
            "AND t.date >= :startDate AND t.date <= :endDate " +
            "AND (:accountId IS NULL OR t.account.id = :accountId) " +
            "AND (:creditCardId IS NULL OR t.creditCard.id = :creditCardId) " +
            "GROUP BY c.id, c.name, c.color, c.icon " +
            "ORDER BY SUM(t.amount) DESC")
    List<CategoryExpenseDTO> findExpensesByCategory(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("accountId") Long accountId,
            @Param("creditCardId") Long creditCardId
    );

    @Query("SELECT t.date, t.type, SUM(t.amount) FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.deleted = false " +
            "AND t.date >= :startDate AND t.date <= :endDate " +
            "AND (:accountId IS NULL OR t.account.id = :accountId) " +
            "AND (:creditCardId IS NULL OR t.creditCard.id = :creditCardId) " +
            "GROUP BY t.date, t.type")
    List<Object[]> findEvolutionData(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("accountId") Long accountId,
            @Param("creditCardId") Long creditCardId
    );
}
