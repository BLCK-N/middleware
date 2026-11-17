package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.PendingOnboarding;
import com.myrtle.middleware.model.PendingOnboarding.Status;
import com.myrtle.middleware.model.PendingOnboarding.Type;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface PendingOnboardingRepository extends JpaRepository<PendingOnboarding, String> {

    Optional<PendingOnboarding> findFirstByEmailIgnoreCaseAndOnboardingTypeAndStatusNotOrderByCreatedAtDesc(
            String email, Type onboardingType, Status status);

    @Query("""
            select p from PendingOnboarding p
            where p.status in :statuses
              and p.lastActivityAt < :cutoff
            order by p.lastActivityAt asc
            """)
    Page<PendingOnboarding> findDueForFollowUp(
            @Param("statuses") Collection<Status> statuses,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);
}
