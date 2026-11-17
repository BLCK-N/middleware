package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.myrtle.middleware.enums.ActivationStatus;
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
        name = "biometric_credentials",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_biometric_credentials_device_id", columnNames = "device_id")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BiometricCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "key_id")
    private String keyId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private DeviceDetails device;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "algorithm", nullable = false)
    private String algorithm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActivationStatus status = ActivationStatus.ACTIVE;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
}
