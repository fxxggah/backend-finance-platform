package com.finance_app.finance_app.account.repository;

import com.finance_app.finance_app.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByIdAndUserId(Long id, Long userId);

    List<Account> findAllByUserId(Long userId);

    List<Account> findAllByUserIdAndArchivedFalse(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    long countByUserIdAndArchivedFalse(Long userId);

    void deleteAllByUserId(Long userId);
}

