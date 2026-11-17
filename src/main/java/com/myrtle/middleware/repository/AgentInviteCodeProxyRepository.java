package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.referral.AgentInviteCodeProxy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentInviteCodeProxyRepository extends JpaRepository<AgentInviteCodeProxy, String> {
    Optional<AgentInviteCodeProxy> findByAgentId(String agentId);

    Optional<AgentInviteCodeProxy> findByInviteCodeIgnoreCase(String inviteCode);

    boolean existsByInviteCodeIgnoreCase(String inviteCode);
}
