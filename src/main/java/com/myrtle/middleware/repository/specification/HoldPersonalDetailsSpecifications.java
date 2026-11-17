package com.myrtle.middleware.repository.specification;

import com.myrtle.middleware.enums.Status;
import com.myrtle.middleware.model.HoldPersonalDetails;
import com.myrtle.middleware.model.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HoldPersonalDetailsSpecifications {

    private HoldPersonalDetailsSpecifications() {
    }

    /**
     * @param status        optional filter by KYC row status
     * @param emailContains optional case-insensitive partial match on user email
     */
    public static Specification<HoldPersonalDetails> withOptionalFilters(Status status, String emailContains) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (emailContains != null && !emailContains.isBlank()) {
                Join<HoldPersonalDetails, User> userJoin = root.join("user", JoinType.INNER);
                String pattern = "%" + emailContains.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(userJoin.get("emailAddress")), pattern));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
