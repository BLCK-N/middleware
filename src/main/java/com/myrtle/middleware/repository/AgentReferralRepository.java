package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.referral.AgentReferrals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentReferralRepository extends JpaRepository<AgentReferrals, String> {
    List<AgentReferrals> findByAgentId(String agentId);

    boolean existsByInvitedUserId(String invitedUserId);
}
