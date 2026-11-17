package com.myrtle.middleware.specification;

import com.myrtle.middleware.enums.MaritalStatus;
import com.myrtle.middleware.model.FamilyOfficeOnboarding;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FamilyOfficeOnboardingSpecification {

    private FamilyOfficeOnboardingSpecification() {
    }

    public static Specification<FamilyOfficeOnboarding> withFilters(
            String name,
            String email,
            String country,
            String city,
            String incomeRange,
            MaritalStatus maritalStatus
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));

            addLike(predicates, cb, root.get("searchName"), name);
            addLike(predicates, cb, root.get("searchEmail"), email);
            addLike(predicates, cb, root.get("searchCountry"), country);
            addLike(predicates, cb, root.get("searchCity"), city);
            addLike(predicates, cb, root.get("searchIncomeRange"), incomeRange);

            if (maritalStatus != null) {
                predicates.add(cb.equal(root.get("searchMaritalStatus"), maritalStatus.name().toLowerCase(Locale.ROOT)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addLike(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Expression<String> expression,
            String value
    ) {
        if (StringUtils.hasText(value)) {
            predicates.add(cb.like(expression, "%" + value.trim().toLowerCase(Locale.ROOT) + "%"));
        }
    }
}
