package com.myrtle.middleware.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "third_party_activities_audit_log",
        indexes = {
                @Index(name = "idx_activity_session_id", columnList = "session_id"),
                @Index(name = "idx_request_time", columnList = "request_time")
        })
public class ThirdPartyActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String Id;

    private String sessionId;
    private Instant requestTime;
    private String apiEndpoint;
    private String thirdPartyProviderName; // EMAIL, MyWealth, QoreID, etc
    private String httpMethod;
    private String requestHeaders;
    @Column(columnDefinition = "TEXT")
    private String requestPayload;
    private String responseHeaders;
    @Column(columnDefinition = "TEXT")
    private String responsePayload;
    private Integer httpStatusCode;
    private Instant requestTimestamp;
    private Instant responseTimestamp;
    private Instant createdAt;
}
