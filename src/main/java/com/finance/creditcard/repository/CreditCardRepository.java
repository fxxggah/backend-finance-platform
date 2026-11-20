package com.finance.creditcard.repository;

import com.finance.creditcard.entity.CreditCard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {

    Optional<CreditCard> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreditCard c WHERE c.id = :id AND c.user.id = :userId")
    Optional<CreditCard> lockByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<CreditCard> findAllByUserId(Long userId);

    List<CreditCard> findAllByUserIdAndArchivedFalse(Long userId);

    org.springframework.data.domain.Page<CreditCard> findAllByUserIdAndArchivedFalse(Long userId,
            org.springframework.data.domain.Pageable pageable);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    void deleteAllByUserId(Long userId);
}
