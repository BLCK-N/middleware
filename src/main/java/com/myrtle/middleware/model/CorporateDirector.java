package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A director of a corporate account that has been created.
 *
 * <p>Copied from {@link PendingCorporateDirector} at completion. The staged record is deleted once
 * the account exists, and it cascades to its directors - so without this the only surviving record
 * of who a company's directors are would be the opaque JSON in
 * {@code corporate_clients.contacts_payload}.
 *
 * <p>Holds what was actually submitted to MyWealth, so it stands as the account's director history.
 */
@Entity
@Table(name = "corporate_directors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CorporateDirector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corporate_client_id", nullable = false)
    @JsonIgnore
    private CorporateClient corporateClient;

    @Column(nullable = false)
    private String firstName;

    private String middleName;

    @Column(nullable = false)
    private String lastName;

    private String email;

    private String phoneNumber;

    @Column(length = 11)
    private String bvn;

    /** MyWealth contact role, e.g. CORP_DIRECTOR. */
    private String role;

    private boolean beneficialOwner;

    private String gender;
    private String title;

    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postCode;
    private String country;
    private String birthDate;
    private String maritalStatus;

    /** When this director was recorded against the created account. */
    private LocalDateTime createdAt = LocalDateTime.now();

    public static CorporateDirector from(PendingCorporateDirector staged, CorporateClient corporateClient) {
        CorporateDirector director = new CorporateDirector();
        director.setCorporateClient(corporateClient);
        director.setFirstName(staged.getFirstName());
        director.setMiddleName(staged.getMiddleName());
        director.setLastName(staged.getLastName());
        director.setEmail(staged.getEmail());
        director.setPhoneNumber(staged.getPhoneNumber());
        director.setBvn(staged.getBvn());
        director.setRole(staged.getRole());
        director.setBeneficialOwner(staged.isBeneficialOwner());
        director.setGender(staged.getGender());
        director.setTitle(staged.getTitle());
        director.setAddressLine1(staged.getAddressLine1());
        director.setAddressLine2(staged.getAddressLine2());
        director.setCity(staged.getCity());
        director.setState(staged.getState());
        director.setPostCode(staged.getPostCode());
        director.setCountry(staged.getCountry());
        director.setBirthDate(staged.getBirthDate());
        director.setMaritalStatus(staged.getMaritalStatus());
        return director;
    }
}
