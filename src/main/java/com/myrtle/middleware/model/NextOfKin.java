package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(
        name = "next_of_kin",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_next_of_kin_user_phone", columnNames = {"user_id", "phone_number"}),
                @UniqueConstraint(name = "uk_next_of_kin_corporate_phone", columnNames = {"corporate_client_id", "phone_number"})
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NextOfKin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_client_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private CorporateClient corporateClient;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "relationship", nullable = false)
    private String relationship;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void normalize() {
        if ((this.user == null && this.corporateClient == null) || (this.user != null && this.corporateClient != null)) {
            throw new IllegalStateException("Next of kin must belong to either a user or a corporate client.");
        }
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase(Locale.ROOT);
        }
        if (this.fullName != null) {
            this.fullName = this.fullName.trim();
        }
        if (this.relationship != null) {
            this.relationship = this.relationship.trim();
        }
        if (this.address != null) {
            this.address = this.address.trim();
        }
        if (this.phoneNumber != null) {
            this.phoneNumber = this.phoneNumber.trim();
        }
    }
}
