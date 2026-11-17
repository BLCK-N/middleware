package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.dtos.request.TransactionRequest;
import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponse {
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
    private TransactionRequest.TransactionType type;
    private TransactionRequest.Channel channel;
    private Status status;
    private String reference;
    private String notes;
    private String transNo;
    private String transDesc;
    private String transDate;
    private String valueDate;
    private TransactionRequest.CurrencyCode currency;
    private double amount;
    private double processingFee;
    private TransactionRequest.DestAccountType destAccountType;
    private String destAccountLabel;
    private String destAccountNo;
    private String destAccountId;
    private String destGlAccountId;
    private TransactionRequest.CurrencyCode destAccountCurrency;
    private boolean autoApprove;
    private String refCode;
    private String accountLabel;
    private String accountNo;
    private String accountId;

    public enum Status {
        PENDING, APPROVED, REJECTED, CANCELLED
    }
}