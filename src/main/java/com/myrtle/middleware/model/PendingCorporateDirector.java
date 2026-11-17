package com.myrtle.middleware.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pending_corporate_director")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingCorporateDirector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "pending_corporate_data_id", nullable = false)
    private String pendingCorporateDataId;

    @Column(nullable = false)
    private String firstName;

    private String middleName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false, length = 11)
    private String bvn;

    /**
     * MyWealth contact role, e.g. CORP_DIRECTOR.
     */
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
}
