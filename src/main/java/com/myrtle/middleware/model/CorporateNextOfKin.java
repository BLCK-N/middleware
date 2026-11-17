package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "corporate_next_of_kin")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CorporateNextOfKin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corporate_client_id", nullable = false)
    @JsonIgnore
    private CorporateClient corporateClient;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "relationship", nullable = false)
    private String relationship;

    @Column(name = "email")
    private String email;

    @Column(name = "mobile_no", nullable = false)
    private String mobileNo;

    @Column(name = "address", columnDefinition = "TEXT")
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
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase(Locale.ROOT);
        }
        if (this.mobileNo != null) {
            this.mobileNo = this.mobileNo.trim();
        }
        if (this.relationship != null) {
            this.relationship = this.relationship.trim().toUpperCase(Locale.ROOT);
        }
        if (this.firstName != null) {
            this.firstName = this.firstName.trim();
        }
        if (this.lastName != null) {
            this.lastName = this.lastName.trim();
        }
        if (this.address != null) {
            this.address = this.address.trim();
        }
    }
}
