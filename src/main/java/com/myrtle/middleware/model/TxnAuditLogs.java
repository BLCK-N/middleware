package com.myrtle.middleware.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "user_activity_audit_log",
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_session_id", columnList = "session_id"),
                @Index(name = "idx_user_email", columnList = "user_email"),
                @Index(name = "idx_event_timestamp", columnList = "event_timestamp"),

        })
public class TxnAuditLogs {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private Instant eventTimestamp = Instant.now();
    private Instant createdAt = Instant.now();

    private String sessionId = String.valueOf(UUID.randomUUID());
    private String userId;
    private String userEmail;
    private String operation;

    private String httpMethod;
    private String endpoint;
    @Column(columnDefinition = "TEXT")
    private String requestPayload;
    private String clientIp;

    private String oldValue;
    private String newValue;

    private String channel; // WEB, MOBILE
    private Integer httpStatusCode;
    @Column(columnDefinition = "TEXT")
    private String responsePayload;
    private String exceptionMessage;
    private Boolean isSuccessful;
    private String action_description;
}
