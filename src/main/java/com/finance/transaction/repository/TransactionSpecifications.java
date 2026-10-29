package com.finance.transaction.repository;

import com.finance.transaction.dto.request.TransactionFilter;
import com.finance.transaction.entity.Transaction;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /**
     * Sempre filtra por usuário e ignora transações excluídas (soft delete).
     */
    public static Specification<Transaction> filter(Long userId, TransactionFilter f) {
        return (root, query, cb) -> {
            // Evita N+1 na listagem (não aplica na query de count)
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                root.fetch("category", JoinType.INNER);
                root.fetch("account", JoinType.LEFT);
                root.fetch("creditCard", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            predicates.add(cb.isFalse(root.get("deleted")));

            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), f.to()));
            }
            if (f.type() != null) {
                predicates.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            }
            if (f.accountId() != null) {
                predicates.add(cb.equal(root.get("account").get("id"), f.accountId()));
            }
            if (f.creditCardId() != null) {
                predicates.add(cb.equal(root.get("creditCard").get("id"), f.creditCardId()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String escaped = f.q().trim().toLowerCase()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(root.get("notes")), pattern, '\\')));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}