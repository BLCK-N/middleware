package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvestmentAccountResponse {
    private String id;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private boolean deleted;
    private double totalValue;
    private String clientId;
    private String mgmtType;
    private String status;
    private String accountUsage;
    private String tradingAccountNo;
    private String tradingSubAccountNo;
    private String accountNo;
    private String accountLabel;
    private String clientCode;
    private String clientLabel;
    private String notes;
    private String currency;
    private double cashBalance;
    private LocalDateTime balanceTime;
    private Double marginInterestRate;
    private Double marginLimit;
    private Double commissionOverride;
    private String refCode;
    private LocalDate accountOpenDate;
    private boolean autoApprove;
    private String dividendDistribution;
    private boolean directCashSettlement;
    private String clearingAccountNo;
    private Double maxOpenBuyLimit;
    private Double maxDailyBuyLimit;
    private Double cashInterestRate;
    private String custodianId;
    private String custodianLabel;
    private String agentId;
    private String agentLabel;
    private String csdSyncStatus;
    private String csdSyncMessage;
    private List<Object> tradingAccounts;  // Adjust to proper type if known
    private String marginExpirationDate;
    private String productRequests;
    private String groupId;
    private String branchId;
    private boolean marginAvailable;
    private String productCode;
    private String productId;
    private Double effectiveCommissionRate;
    private String glAccountId;
    private String branchCode;
    private String branchLabel;
    private String productLabel;
    private String groupLabel;
    private String groupCode;
    private boolean productCsdIntegrated;
}
