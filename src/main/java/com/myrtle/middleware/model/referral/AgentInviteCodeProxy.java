package com.myrtle.middleware.model.referral;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Locale;

@Data
@Entity
@Table(
        name = "agent_invite_code_proxy",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_agent_invite_code", columnNames = "invite_code"),
                @UniqueConstraint(name = "uk_agent_invite_agent_id", columnNames = "agent_id")
        },
        indexes = {
                @Index(name = "idx_agent_invite_code", columnList = "invite_code"),
                @Index(name = "idx_agent_invite_agent_id", columnList = "agent_id")
        }
)
public class AgentInviteCodeProxy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "invite_code", nullable = false)
    private String inviteCode;

    @Column(name = "label")
    private String label;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updateAt;

    @PrePersist
    @PreUpdate
    private void normalizeInviteCode() {
        if (inviteCode != null) {
            inviteCode = inviteCode.trim().toUpperCase(Locale.ROOT);
        }
    }
}
