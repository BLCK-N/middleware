package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.myrtle.middleware.enums.ActivationStatus;
import com.myrtle.middleware.enums.DevicePlatform;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(
        name = "device_details",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_details_user_id", columnNames = "user_id")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DeviceDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private User user;

    @Column(name = "client_device_id", nullable = false)
    private String clientDeviceId;

    @Column(name = "device_name")
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform")
    private DevicePlatform platform;

    @Column(name = "app_version")
    private String appVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActivationStatus status = ActivationStatus.ACTIVE;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @OneToOne(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private BiometricCredential biometricCredential;
}
