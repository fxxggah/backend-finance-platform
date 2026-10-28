package com.finance_app.finance_app.creditcard.repository;

import com.finance_app.finance_app.creditcard.entity.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {

    Optional<CreditCard> findByIdAndUserId(Long id, Long userId);

    List<CreditCard> findAllByUserId(Long userId);

    List<CreditCard> findAllByUserIdAndArchivedFalse(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}

