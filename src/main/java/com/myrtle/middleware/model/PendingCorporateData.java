package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.myrtle.middleware.enums.Gender;
import com.myrtle.middleware.model.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "pending_corporate_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PendingCorporateData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String bvn;
    private Gender gender;
    private String inviteCode;
    private boolean emailVerified = false;
    private boolean phoneVerified = false;

    private String memartUrl;

    private String rcNumber;

    private String companyName;

    private String companyType;

    /**
     * MyWealth businessStructure; inferred from companyType when blank.
     */
    private String businessStructure;

    private OffsetDateTime registrationDate;

    private String classification;

    private String companyStatus;

    private String companyEmail;

    private String headOfficeAddress;

    private String branchAddress;

    private String addressLine2;

    private String state;

    private String city;

    private String lga;

    private String postCode;

    private String country;

    private BigDecimal shareCapital;

    private String shareCapitalInWords;

    private Integer numberOfAffiliates;

    private boolean cacVerified = false;

    /**
     * Checkpoint: the MyWealth client id, written as soon as the (irreversible) upstream client is
     * created. Persisted in its own transaction so it survives a rollback of the rest of completion,
     * letting a retry resume instead of creating a second client.
     */
    private String externalClientId;

    /**
     * Checkpoint: the MyWealth investment account id, recorded the same way.
     */
    private String externalInvestmentId;

    private String username;

    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    private String password;

    private String valuationCurrency;

    private String profession;

    private String nationality;

    private String netWorth;
    private String annualIncome;
    private String liquidNetWorth;
    private String sourceOfWealth;
    private String investmentExperience;
    private String riskTolerance;
    private String investmentObjective;

    private ID_TYPE idType;
    private String idExpiry;
    private String date;
    private String idIssue;
    private String issuingAuthority;
    private String placeOfIssue;
    private String personalInformation;
    private String maritalStatus;
    private String title;
    private String mothersMaidenName;
    private String stateOfOrigin;
    private String localGovernment;
    private String employerName;
    private String employerAddress;
    private String address;

    /**
     * Encrypted at rest for the same reason as {@link #password}.
     */
    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    private String pin;


    @JsonIgnore
    @Transient
    private List<PendingCorporateDirector> directors = new ArrayList<>();

    public enum ID_TYPE {
        NATIONAL_IDENTITY,
        FINANCIAL_ID,
        DRIVERS_LICENSE
    }


    public void replaceDirectors(List<PendingCorporateDirector> newDirectors) {
        this.directors = new ArrayList<>();
        if (newDirectors == null || newDirectors.isEmpty()) {
            return;
        }
        for (PendingCorporateDirector director : newDirectors) {
            director.setPendingCorporateDataId(this.id);
            this.directors.add(director);
        }
    }

    @PrePersist
    @PreUpdate
    private void normalizeEmail() {
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
