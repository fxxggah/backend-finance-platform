package com.finance.transfer.repository;

import com.finance.transfer.entity.Transfer;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TransferSpecification {

    public static Specification<Transfer> withFilters(Long userId, LocalDate from, LocalDate to, Long accountId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Sempre filtra pelo usuário logado
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), to));
            }
            if (accountId != null) {
                Predicate isFromAccount = cb.equal(root.get("fromAccount").get("id"), accountId);
                Predicate isToAccount = cb.equal(root.get("toAccount").get("id"), accountId);
                // A conta pode ser tanto a origem quanto o destino da transferência
                predicates.add(cb.or(isFromAccount, isToAccount));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}