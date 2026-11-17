package com.myrtle.middleware.specification;

import com.myrtle.middleware.dtos.TransactionFilterRequest;
import com.myrtle.middleware.model.Transaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class TransactionSpecification {

    public static Specification<Transaction> withFilters(TransactionFilterRequest filter, String userId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // always filter by user
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (filter.getType() != null) {
                predicates.add(cb.equal(root.get("type"), filter.getType()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getAction() != null) {
                predicates.add(cb.equal(root.get("action"), filter.getAction()));
            }

            if (filter.getSource() != null && !filter.getSource().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("source")), "%" + filter.getSource().toLowerCase() + "%"));
            }

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String searchLower = "%" + filter.getSearch().toLowerCase() + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("narration")), searchLower),
                                cb.like(cb.lower(root.get("txRef")), searchLower),
                                cb.like(cb.lower(root.get("source")), searchLower)
                        )
                );
            }

            if (filter.getCreatedAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getCreatedAfter()));
            }
            if (filter.getCreatedBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getCreatedBefore()));
            }

            if (filter.getUpdatedAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), filter.getUpdatedAfter()));
            }
            if (filter.getUpdatedBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("updatedAt"), filter.getUpdatedBefore()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
