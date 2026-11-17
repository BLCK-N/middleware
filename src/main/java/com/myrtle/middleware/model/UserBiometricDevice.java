package com.myrtle.middleware.model;

import com.myrtle.middleware.enums.DevicePlatform;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "user_biometric_devices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_biometric_device_user_device", columnNames = {"user_id", "device_id"}),
                @UniqueConstraint(name = "uk_user_biometric_device_hash", columnNames = "bio_token_hash")
        },
        indexes = {
                @Index(name = "idx_user_biometric_device_user", columnList = "user_id"),
                @Index(name = "idx_user_biometric_device_expires_at", columnList = "expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserBiometricDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 32)
    private DevicePlatform deviceType;

    @Column(name = "bio_token_hash", nullable = false, unique = true, length = 64)
    private String bioTokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.expiresAt == null) {
            this.expiresAt = now.plusSeconds(30L * 24L * 60L * 60L);
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt == null || expiresAt.isBefore(Instant.now());
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
