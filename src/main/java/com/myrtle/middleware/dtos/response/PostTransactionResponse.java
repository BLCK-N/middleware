package com.myrtle.middleware.dtos.response;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostTransactionResponse {

    private String id;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
    private boolean deleted;

    private String journalId;
    private String transSrcAccountId;
    private String idemPotencyId;
    private String branchId;
    private String clientId;
    private String clientCode;
    private String clientLabel;

    private String type;       // e.g., DEPOSIT
    private String channel;    // e.g., CASH
    private String status;     // e.g., PENDING
    private String reference;
    private String notes;

    private String transNo;
    private String transDesc;
    private String transDate;
    private String valueDate;

    private String currency;
    private double amount;
    private double processingFee;

    private String destAccountType;
    private String destAccountLabel;
    private String destAccountNo;
    private String destAccountId;
    private String destGlAccountId;
    private String destAccountCurrency;

    private boolean autoApprove;
    private String refCode;

    private String accountLabel;
    private String accountNo;
    private String accountId;
}
