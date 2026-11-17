package com.myrtle.middleware.model;

import com.myrtle.middleware.enums.RefreshSubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_subject", columnList = "subject_type, subject_id"),
        @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 32)
    private RefreshSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
