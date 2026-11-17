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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "user_agents_table",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_agents_user_id", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_user_agents_agent_id", columnNames = "agent_id")
        },
        indexes = {
                @Index(name = "idx_user_agents_user_id", columnList = "user_id"),
                @Index(name = "idx_user_agents_agent_id", columnList = "agent_id")
        }
)
public class UserAgentsTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "agent_id", nullable = false)
    private String agentId;
    @Column(name = "my_wealth_ref_code")
    private String myWealthRefCode;

    @Column(name = "email")
    private String email;

    @Column(name = "label")
    private String label;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "payout_enabled", nullable = false)
    private boolean payoutEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
