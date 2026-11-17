package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactPatch {

    private String id;
    private String role;
    private String maritalStatus;
    private String gender;

    private String refCode;
    private String label;
    private String title;
    private String notes;

    private String firstName;
    private String middleName;
    private String lastName;

    private String email;
    private Long mobileNo;
    private Long officeNo;

    private AddressPatch address; // ✅ nested object

    private String birthDate; // ISO: yyyy-MM-dd
    private String idNo;
    private String finIdNo; // ⚠️ safer as String

    private String idType;
    private String idExpDate;

    private String profession;
    private String nationality;

    private String networthTotalRange;
    private String annualIncomeRange;
    private String networthLiquidRange;

    private String sourceOfWealth;
    private String investmentExperience;
    private String riskTolerance;

    private String employerName;
    private String employerAddress; // ⚠️ was "1" → make String

    private String investmentObj;

    private Boolean moneyLaunderingRisk;
    private Boolean politicallyExposed;
    private Boolean criminalConviction;

    private String riskRating;

    private String idIssueDate;
    private String motherMaidenName;

    private String stateOfOrigin;
    private String localGovt;

    private String idIssuingAuthority;
    private String idPlaceOfIssue;
}
