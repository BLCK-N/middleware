package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateClientRequest {

    private String clientType;
    private String businessStructure;
    private String refCode;

    private String label;
    private String notes;

    private String email;
    private String notificationEmail;

    private Long mobileNo;
    private Long officeNo;

    private String valuationCurrency;

    private String groupId;
    private String advisorGroupId;
    private String kycTierId;

    private List<AddressPatch> address;
    private List<ContactPatch> contact;
    private List<SettlementBankPatch> settlementBanks;

    private String picture;
    private String businessIncCode;

    private Boolean autoApprove; // ✅ NEW
    private String relationshipStartDate;
    private String advisorId;
    private String businessIncDate;

    private List<ProductRequest> productRequests; // ✅ NEW

    private String referrerLabel;
    private String kycStatus;
    private Boolean notificationEnabled;
    private String referrerId;
}