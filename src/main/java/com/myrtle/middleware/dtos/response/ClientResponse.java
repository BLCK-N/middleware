package com.myrtle.middleware.dtos.response;

import lombok.Data;
import java.util.List;

@Data
public class ClientResponse {

    private String id;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
    private Boolean deleted;

    private String clientType;
    private String businessStructure;
    private String businessIncCode;
    private String status;
    private String accessSensitivity;
    private String accessTag;
    private String kycStatus;

    private String code;
    private String refCode;
    private String relationshipStartDate;
    private String label;
    private String notes;

    private Long mobileNo;
    private Long officeNo;

    private String referralCode;
    private String referrerLabel;
    private String referrerId;

    private List<Address> address;
    private List<SettlementBank> settlementBanks;
    private List<Contact> contact;

    private String valuationCurrency;
    private String email;
    private String notificationEmail;
    private String pictureUrl;

    private String businessIncDate;
    private Boolean notificationEnabled;

    private String groupId;
    private String advisorMobileNo;
    private String advisorOfficeNo;

    private String kycTierId;
    private String kycTierCode;
    private String kycTierLabel;

    private String groupCode;
    private String groupLabel;

    private Boolean loyaltyEnabled;

    private String advisorGroupId;
    private String advisorGroupCode;
    private String advisorGroupLabel;

    private String advisorId;
    private String advisorCode;
    private String advisorLabel;
    private String advisorEmail;

    // ================= NESTED =================

    @Data
    public static class Address {
        private String id;
        private String type;
        private Object addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private Integer postCode;
        private String country;
    }

    @Data
    public static class SettlementBank {
        private String id;
        private String status;
        private String type;
        private String stlBankName;
        private Integer stlBankRoutingNo;
        private String stlBankSwiftCode;
        private String stlBankBranch;
        private Object stlBankAddress;
        private String stlBankAccountName;
        private Long stlBankAccountNo;
        private String stlBankAccountOpenDate;
        private String approvedBy;
        private String approvedDate;
        private String suspendedBy;
        private String suspendedDate;
        private String currency;
    }

    @Data
    public static class Contact {
        private String id;
        private String createdBy;
        private String createdAt;
        private String updatedBy;
        private String updatedAt;
        private Boolean deleted;

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

        private Address address;

        private String username;
        private Boolean grantLoginAccess;
        private Boolean passwordChangeRequired;
        private Boolean pinChangeRequired;

        private String lastPinChange;
        private String lastPasswdChange;
        private String lastLoginIPAddress;
        private Integer failedLoginAttempts;
        private String lastLoginTime;

        private String birthDate;
        private String idNo;
        private Object finIdNo;

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
        private Object employerAddress;

        private String investmentObj;
        private Boolean moneyLaunderingRisk;
        private String riskRating;

        private Boolean politicallyExposed;
        private Boolean criminalConviction;

        private String pictureUrl;
        private String signatureUrl;

        private String idIssueDate;
        private String idIssuingAuthority;
        private String idPlaceOfIssue;

        private String motherMaidenName;
        private String stateOfOrigin;
        private String localGovt;

        private String otpStatus;
        private String otpSecret;
        private String otpMethod;
        private String otpEnrolledAt;
        private String otpDisabledAt;
        private String otpLastVerifiedAt;
    }
}