package com.myrtle.middleware.specification;

import com.myrtle.middleware.dtos.InvestmentFilterRequest;
import com.myrtle.middleware.model.Investment;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class InvestmentSpecification {

    public static Specification<Investment> withFilters(InvestmentFilterRequest filter, String productId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("product").get("id"), productId));

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (filter.getUserId() != null && !filter.getUserId().isBlank()) {
                predicates.add(cb.equal(root.get("user").get("id"), filter.getUserId()));
            }

            if (filter.getOrderType() != null) {
                predicates.add(cb.equal(root.get("orderType"), filter.getOrderType()));
            }

            if (filter.getCurrency() != null) {
                predicates.add(cb.equal(root.get("currency"), filter.getCurrency()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("investmentStatus"), filter.getStatus()));
            }

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String search = "%" + filter.getSearch().toLowerCase() + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("accountId")), search),
                                cb.like(cb.lower(root.get("marketCode")), search),
                                cb.like(cb.lower(root.get("secId")), search),
                                cb.like(cb.lower(root.get("notes")), search),
                                cb.like(cb.lower(root.get("refCode")), search)
                        )
                );
            }

            if (filter.getCreatedAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getCreatedAfter()));
            }

            if (filter.getCreatedBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getCreatedBefore()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
