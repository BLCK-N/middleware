package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.myrtle.middleware.enums.AccountOwnerType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "withdrawal_accounts")
public class WithdrawalAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String bankCode;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false)
    private String accountName;

    @Column
    private String bankVerificationNumber;

    /**
     * Reference to User (for normal user withdrawal accounts).
     * Can be null if ownerType is CORPORATE_ACCOUNT.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", nullable = true)
    @JsonIgnore
    private User user;

    /**
     * Reference to CorporateClient (for corporate account settlement accounts).
     * Can be null if ownerType is USER.
     */
    @Column(name = "corporate_client_id")
    private String corporateClientId;

    /**
     * Identifies whether this withdrawal account belongs to a normal user or a corporate account.
     * Default is USER for backward compatibility.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type")
    private AccountOwnerType ownerType = AccountOwnerType.USER;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
