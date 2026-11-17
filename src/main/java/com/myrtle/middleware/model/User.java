package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.myrtle.middleware.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_bvn", columnList = "bvn")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "middle_name", nullable = true)
    private String middleName;

    @Column(name = "email_address", nullable = false, unique = true)
    private String emailAddress;

    @Column(name = "phoneNumber", nullable = false, unique = false)
    private String phoneNumber;

    @JsonIgnore
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "clientId", nullable = false)
    private String clientId;

    @Column(name = "investmentAccountId", nullable = true)
    private String investmentAccountId;

    @Column(name = "branchId", nullable = false)
    private String branchId;

    @JsonIgnore
    @Column(name = "pin", nullable = true)
    private String pin;

    @Column(name = "usePin")
    private boolean usePin;

    @Column(name = "accountNumber")
    private String accountNumber;

    @Column(name = "nin", nullable = true)
    private String nin;

    @Column(name = "last_balance")
    private Double last_balance = 0.0;

    public double getLast_balance() {
        return last_balance == null ? 0.0 : last_balance;
    }

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER; // default for old/existing users

    @Column(name = "tier", nullable = false)
    private String tier;

    @Column(name = "gender", nullable = false)
    private String gender;

    @Column(name = "isDeleted", nullable = false, columnDefinition = "boolean default false")
    private boolean isDeleted = false;

    @Column(name = "isDisabled", nullable = false, columnDefinition = "boolean default false")
    private boolean isDisabled = false;

    @Column(name = "profileImage", nullable = true)
    private String profileImage = null;

    @Column(name = "address", nullable = true)
    private String address;

    @Column(name = "isPep", nullable = false, columnDefinition = "boolean default false")
    private boolean isPep = false;

    @Column(name = "pepPosition", nullable = true)
    private String pepPosition = null;

    @Column
    @CreatedDate
    private LocalDateTime createdAt;

    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;


    //@JsonIgnore
    @Column(name = "bvn", nullable = false)
    private String bvn;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore  // avoid recursion when serializing
    private List<Transaction> transactions;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore  // to avoid infinite recursion when serializing
    private List<Investment> investments = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_client_id")
    @JsonIgnore
    private CorporateClient corporateClient;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private DeviceDetails deviceDetails;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(() -> "ROLE_" + role.name());
    }

    @Override
    public String getUsername() {
        return this.emailAddress;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }


    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !this.isDisabled; // if disabled → locked
    }

    @Override
    public boolean isEnabled() {
        return !this.isDeleted; // if deleted → not enabled
    }

    @PrePersist
    @PreUpdate
    private void normalizeEmailAddress() {
        if (this.emailAddress != null) {
            this.emailAddress = this.emailAddress.trim().toLowerCase(Locale.ROOT);
        }
    }

}
