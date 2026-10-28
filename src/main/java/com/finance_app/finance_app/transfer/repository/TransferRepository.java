package com.finance_app.finance_app.transfer.repository;

import com.finance_app.finance_app.transfer.entity.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long>, JpaSpecificationExecutor<Transfer> {

    Optional<Transfer> findByIdAndUserId(Long id, Long userId);

    Page<Transfer> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByFromAccountIdOrToAccountId(Long fromAccountId, Long toAccountId);

    void deleteAllByUserId(Long userId);
}

