package com.myrtle.middleware.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "otps")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserOtp {
    private static final Integer otpTtlSeconds = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String email;
    private String otp;
    private boolean isVerified = false;
    private String otpReference;
    private LocalDateTime timeGenerated = LocalDateTime.now();
    private LocalDateTime timeUsed;

    @PrePersist
    @PreUpdate
    private void normalizeEmail() {
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase(Locale.ROOT);
        }
    }

    /** How long a completed verification keeps counting as proof the inbox is reachable. */
    private static final Integer verificationValidityHours = 24;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.timeGenerated.plusSeconds(otpTtlSeconds));
    }

    /** Marks the OTP as used. Records {@code timeUsed}, which is what freshness is measured from. */
    public void markVerified() {
        this.isVerified = true;
        this.timeUsed = LocalDateTime.now();
    }

    /**
     * True when this OTP was verified recently enough to still gate account creation. A verification
     * from months ago proves nothing about who controls the inbox today.
     *
     * <p>Rows verified before {@code timeUsed} started being recorded are treated as stale, so they
     * fail closed rather than granting indefinite access.
     */
    public boolean isVerificationFresh() {
        if (!this.isVerified) {
            return false;
        }
        if (this.timeUsed == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(this.timeUsed.plusHours(verificationValidityHours));
    }
}
