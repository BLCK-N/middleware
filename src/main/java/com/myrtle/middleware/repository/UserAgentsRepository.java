package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.referral.UserAgentsTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAgentsRepository extends JpaRepository<UserAgentsTable, String> {
    Optional<UserAgentsTable> findByUserId(String userId);

    Optional<UserAgentsTable> findByAgentId(String agentId);

    boolean existsByUserId(String userId);
}
