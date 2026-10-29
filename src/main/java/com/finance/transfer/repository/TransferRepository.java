package com.finance.transfer.repository;

import com.finance.transfer.entity.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long>, JpaSpecificationExecutor<Transfer> {

    Optional<Transfer> findByIdAndUserId(Long id, Long userId);

    Page<Transfer> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByFromAccountIdOrToAccountId(Long fromAccountId, Long toAccountId);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t WHERE t.fromAccount.id = :accountId")
    BigDecimal sumAmountSentByAccountId(@Param("accountId") Long accountId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t WHERE t.toAccount.id = :accountId")
    BigDecimal sumAmountReceivedByAccountId(@Param("accountId") Long accountId);

    void deleteAllByUserId(Long userId);
}
