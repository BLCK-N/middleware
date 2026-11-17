package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatchClientRequest {

    private String clientType;
    private String businessStructure;
    private String refCode;
    private String accessTag;
    private String accessSensitivity;
    private String label;
    private String notes;
    private String email;
    private String notificationEmail;
    private String mobileNo;
    private String officeNo;
    private String valuationCurrency;
    private String groupId;
    private String advisorGroupId;
    private String kycTierId;

    private List<Address> address;
    private List<Contact> contact;

    private String picture;
    private String businessIncCode;
    private String relationshipStartDate;
    private String advisorId;
    private String businessIncDate;

    private List<SettlementBank> settlementBanks;

    private String referrerLabel;
    private String kycStatus;
    private Boolean notificationEnabled;
    private String referrerId;

    // ================= NESTED =================

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Address {
        private String id;
        private String type;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postCode;
        private String country;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Contact {
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
        private String mobileNo;
        private String officeNo;

        private Address address;

        private String birthDate;
        private String idNo;
        private String finIdNo;
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
        private String employerAddress;

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

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SettlementBank {
        private String id;
        private String type;
        private String stlBankName;
        private String stlBankRoutingNo;
        private String stlBankSwiftCode;
        private String stlBankBranch;
        private String stlBankAddress;
        private String stlBankAccountName;
        private String stlBankAccountNo;
        private String stlBankAccountOpenDate;
        private String currency;
    }
}
