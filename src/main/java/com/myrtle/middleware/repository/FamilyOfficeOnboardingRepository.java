package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.FamilyOfficeOnboarding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface FamilyOfficeOnboardingRepository extends JpaRepository<FamilyOfficeOnboarding, String>,
        JpaSpecificationExecutor<FamilyOfficeOnboarding> {

    Optional<FamilyOfficeOnboarding> findByIdAndDeletedFalse(String id);

    Page<FamilyOfficeOnboarding> findByDeletedFalse(Pageable pageable);
}
