package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.myrtle.middleware.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(
        name = "corporate_clients",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_corporate_clients_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_corporate_clients_bvn", columnNames = "bvn"),
                @UniqueConstraint(name = "uk_corporate_clients_mobile_no", columnNames = "mobile_no"),
                @UniqueConstraint(name = "uk_corporate_clients_primary_contact_email", columnNames = "primary_contact_email"),
                @UniqueConstraint(name = "uk_corporate_clients_primary_contact_username", columnNames = "primary_contact_username"),
                @UniqueConstraint(name = "uk_corporate_clients_primary_contact_mobile_no", columnNames = "primary_contact_mobile_no"),
                @UniqueConstraint(name = "uk_corporate_clients_ref_code", columnNames = "ref_code")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CorporateClient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "external_client_id")
    private String externalClientId;

    @Column(name = "external_client_code")
    private String externalClientCode;

    @Column(name = "external_status")
    private String externalStatus;

    @Column(name = "investmentId")
    private String investmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role = Role.CORPORATE_ACCOUNT;

    @Column(name = "client_type", nullable = false)
    private String clientType;

    @Column(name = "business_structure")
    private String businessStructure;

    @Column(name = "ref_code")
    private String refCode;

    @Column(name = "label")
    private String label;

    @Column(name = "tier")
    private Integer tier = 3;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "notification_email")
    private String notificationEmail;

    @Column(name = "mobile_no", nullable = false)
    private String mobileNo;

    @Column(name = "valuation_currency")
    private String valuationCurrency;

    @Column(name = "group_id")
    private String groupId;

    @Column(name = "auto_approve")
    private Boolean autoApprove;

    @Column(name = "business_inc_date")
    private String businessIncDate;

    @Column(name = "notification_enabled")
    private Boolean notificationEnabled;

    @Column(name = "primary_address", columnDefinition = "TEXT")
    private String primaryAddress;

    @Column(name = "primary_contact_role")
    private String primaryContactRole;

    @Column(name = "primary_contact_first_name")
    private String primaryContactFirstName;

    @Column(name = "primary_contact_middle_name")
    private String primaryContactMiddleName;

    @Column(name = "primary_contact_last_name")
    private String primaryContactLastName;

    @Column(name = "primary_contact_email")
    private String primaryContactEmail;

    @Column(name = "primary_contact_mobile_no")
    private String primaryContactMobileNo;

    @Column(name = "primary_contact_username")
    private String primaryContactUsername;

    @Column(name = "grant_login_access")
    private Boolean grantLoginAccess;

    @JsonIgnore
    @Column(name = "password_hash")
    private String passwordHash;

    @JsonIgnore
    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "pin_set", nullable = false)
    private boolean pinSet = false;

    @Column(name = "pin_enabled", nullable = false)
    private boolean pinEnabled = false;

    @Column(name = "bvn", nullable = false)
    private String bvn;

    @Column(name = "id_no")
    private String idNo;

    @Column(name = "fin_id_no")
    private String finIdNo;

    @Column(name = "id_type", nullable = false)
    private String idType;

    @Column(name = "id_exp_date", nullable = false)
    private String idExpDate;

    @Column(name = "profession", nullable = false)
    private String profession;

    @Column(name = "nationality", nullable = false)
    private String nationality;

    @Column(name = "investment_obj")
    private String investmentObj;

    @Column(name = "source_of_wealth")
    private String sourceOfWealth;

    @Column(name = "investment_experience")
    private String investmentExperience;

    @Column(name = "risk_tolerance")
    private String riskTolerance;

    @Column(name = "risk_rating")
    private String riskRating;

    @Column(name = "money_laundering_risk")
    private Boolean moneyLaunderingRisk;

    @Column(name = "politically_exposed")
    private Boolean politicallyExposed;

    @Column(name = "criminal_conviction")
    private Boolean criminalConviction;

    @Column(name = "employer_name")
    private String employerName;

    @Column(name = "employer_address")
    private String employerAddress;

    // Fields collected during staged corporate onboarding (PendingCorporateData)
    @Column(name = "rc_number")
    private String rcNumber;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_type")
    private String companyType;

    @Column(name = "classification")
    private String classification;

    @Column(name = "company_status")
    private String companyStatus;

    @Column(name = "company_email")
    private String companyEmail;

    @Column(name = "memart_url", columnDefinition = "TEXT")
    private String memartUrl;

    @Column(name = "head_office_address", columnDefinition = "TEXT")
    private String headOfficeAddress;

    @Column(name = "branch_address", columnDefinition = "TEXT")
    private String branchAddress;

    @Column(name = "state")
    private String state;

    @Column(name = "city")
    private String city;

    @Column(name = "lga")
    private String lga;

    @Column(name = "post_code")
    private String postCode;

    @Column(name = "country")
    private String country;

    @Column(name = "share_capital")
    private java.math.BigDecimal shareCapital;

    @Column(name = "share_capital_in_words")
    private String shareCapitalInWords;

    @Column(name = "number_of_affiliates")
    private Integer numberOfAffiliates;

    @Column(name = "cac_verified", nullable = false, columnDefinition = "boolean default false")
    private boolean cacVerified = false;

    @Column(name = "bank_code")
    private String bankCode;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "bank_verified", nullable = false, columnDefinition = "boolean default false")
    private boolean bankVerified = false;

    @Column(name = "addresses_payload", columnDefinition = "TEXT")
    private String addressesPayload;

    @Column(name = "contacts_payload", columnDefinition = "TEXT")
    private String contactsPayload;

    @OneToMany(mappedBy = "corporateClient", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<CorporateNextOfKin> nextOfKins = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "corporateClient",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JsonIgnore
    private List<User> users = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void normalize() {
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase(Locale.ROOT);
        }
        if (this.notificationEmail != null) {
            this.notificationEmail = this.notificationEmail.trim().toLowerCase(Locale.ROOT);
        }
        if (this.primaryContactEmail != null) {
            this.primaryContactEmail = this.primaryContactEmail.trim().toLowerCase(Locale.ROOT);
        }
        if (this.primaryContactUsername != null) {
            this.primaryContactUsername = this.primaryContactUsername.trim().toLowerCase(Locale.ROOT);
        }
        if (this.bvn != null) {
            this.bvn = this.bvn.trim();
        }
        if (this.mobileNo != null) {
            this.mobileNo = this.mobileNo.trim();
        }
        if (this.primaryContactMobileNo != null) {
            this.primaryContactMobileNo = this.primaryContactMobileNo.trim();
        }
        if (this.refCode != null) {
            this.refCode = this.refCode.trim();
        }
        if (this.role == null) {
            this.role = Role.CORPORATE_ACCOUNT;
        }
        if (this.tier == null) {
            this.tier = 3;
        }

        if (this.pinHash != null && !this.pinHash.isBlank()) {
            this.pinSet = true;
            this.pinEnabled = true;
        }
    }
}
