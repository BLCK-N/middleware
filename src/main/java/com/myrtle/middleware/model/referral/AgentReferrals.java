package com.myrtle.middleware.model.referral;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "agent_referrals",
        uniqueConstraints = @UniqueConstraint(name = "uk_agent_referrals_invited_user", columnNames = "invited_user_id"),
        indexes = {
                @Index(name = "idx_agent_referrals_agent_id", columnList = "agent_id"),
                @Index(name = "idx_agent_referrals_invited_user", columnList = "invited_user_id")
        }
)
public class AgentReferrals {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "invited_user_id", nullable = false)
    private String invitedUserId;

    @Column(name = "client_type")
    private String clientType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
