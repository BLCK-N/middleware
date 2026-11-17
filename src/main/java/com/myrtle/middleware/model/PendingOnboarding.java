package com.myrtle.middleware.model;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pending_onboarding", indexes = {
        @Index(name = "idx_pending_onboarding_email_type", columnList = "email, onboarding_type"),
        @Index(name = "idx_pending_onboarding_status_activity", columnList = "status, last_activity_at")
})
@Getter
@Setter
@NoArgsConstructor
public class PendingOnboarding {

    public enum Type { INDIVIDUAL, CORPORATE }

    public enum Status { IN_PROGRESS, PENDING_ONBOARDING, REMINDER_SENT, ACCOUNT_CREATED }

    public enum ExitReason { UNKNOWN, USER_EXITED, VALIDATION_ERROR, KYC_FAILED, DOCUMENT_UPLOAD_FAILED, NETWORK_ERROR, SESSION_EXPIRED }

    public enum Stage {
        OTP_VERIFIED("OTP Verification"),
        BASIC_DETAILS("Basic Details"),
        BVN("BVN Verification"),
        KYB("KYB / CAC Verification"),
        MEMART("MEMART"),
        DIRECTORS("Directors"),
        CREDENTIALS("Credentials"),
        ACCOUNT_CREATION("Account Creation"),
        ACCOUNT_CREATED("Account Created");

        private final String label;

        Stage(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Stage lastCompletedFromCorporatePage(int nextPage) {
            return switch (nextPage) {
                case 2 -> BASIC_DETAILS;
                case 4 -> BVN;
                case 5 -> KYB;
                case 6 -> MEMART;
                case 7 -> DIRECTORS;
                case 8 -> CREDENTIALS;
                default -> OTP_VERIFIED;
            };
        }

        public static Stage currentFromCorporatePage(int nextPage) {
            return switch (nextPage) {
                case 2 -> BVN;
                case 4 -> KYB;
                case 5 -> MEMART;
                case 6 -> DIRECTORS;
                case 7 -> CREDENTIALS;
                case 8 -> ACCOUNT_CREATION;
                default -> BASIC_DETAILS;
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String email;

    private String firstName;
    private String lastName;
    private String phoneNumber;

    @Column(name = "application_id")
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_type", nullable = false)
    private Type onboardingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stage lastCompletedStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stage currentStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.IN_PROGRESS;

    @Enumerated(EnumType.STRING)
    private ExitReason exitReason;

    @Column(name = "otp_reference")
    private String otpReference;

    @Column(nullable = false)
    private int reminderCount = 0;

    private LocalDateTime lastReminderAt;
    private boolean customerReminderSent;
    private boolean internalNotificationSent;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "event_log", columnDefinition = "TEXT")
    private String eventLog;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
