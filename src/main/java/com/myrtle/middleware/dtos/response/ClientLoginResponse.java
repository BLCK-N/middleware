package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.MY_WEALTH_ROLE;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ClientLoginResponse {

    private String id;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private Boolean deleted;

    private String clientType;
    private String businessStructure;
    private String businessIncCode;
    private String status;
    private String kycStatus;

    private String code;
    private String refCode;
    private LocalDate relationshipStartDate;
    private String label;
    private String notes;

    private List<WorkflowNote> workflowNotes;

    private String mobileNo;
    private String officeNo;

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

    private LocalDate businessIncDate;
    private Boolean notificationEnabled;

    private String groupId;
    private Boolean loyaltyEnabled;

    private String advisorOfficeNo;
    private String advisorGroupId;
    private String advisorGroupCode;
    private String advisorGroupLabel;

    private String kycTierId;
    private String kycTierCode;
    private String kycTierLabel;

    private String groupCode;
    private String groupLabel;

    private String advisorId;
    private String advisorCode;
    private String advisorLabel;
    private String advisorEmail;
    private String advisorMobileNo;

    @Data
    public static class WorkflowNote {
        private String text;
        private String createdBy;
        private LocalDateTime createdAt;
    }

    @Data
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
    public static class SettlementBank {
        private String id;
        private String status;
        private String type;

        private String stlBankName;
        private String stlBankRoutingNo;
        private String stlBankSwiftCode;
        private String stlBankBranch;
        private String stlBankAddress;
        private String stlBankAccountName;
        private String stlBankAccountNo;
        private LocalDate stlBankAccountOpenDate;

        private String approvedBy;
        private LocalDateTime approvedDate;
        private String suspendedBy;
        private LocalDateTime suspendedDate;

        private String currency;
    }

    @Data
    public static class Contact {
        private String id;
        private String createdBy;
        private LocalDateTime createdAt;
        private String updatedBy;
        private LocalDateTime updatedAt;
        private Boolean deleted;

        private MY_WEALTH_ROLE role;
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

        private String username;
        private Boolean grantLoginAccess;
        private Boolean passwordChangeRequired;
        private Boolean pinChangeRequired;

        private LocalDateTime lastPinChange;
        private LocalDateTime lastPasswdChange;
        private String lastLoginIPAddress;
        private Integer failedLoginAttempts;
        private LocalDateTime lastLoginTime;

        private LocalDate birthDate;

        private String idNo;
        private String finIdNo;
        private String idType;
        private LocalDate idExpDate;

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
        private String riskRating;
        private Boolean politicallyExposed;
        private Boolean criminalConviction;

        private String pictureUrl;
        private String signatureUrl;

        private LocalDate idIssueDate;
        private String idIssuingAuthority;
        private String idPlaceOfIssue;

        private String motherMaidenName;
        private String stateOfOrigin;
        private String localGovt;
    }


}

