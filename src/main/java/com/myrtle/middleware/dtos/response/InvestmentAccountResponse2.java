package com.myrtle.middleware.dtos.response;

import lombok.Data;
import java.util.List;

@Data
public class InvestmentAccountResponse2 {

    private String id;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
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
    private String balanceTime;

    private double marginInterestRate;
    private double marginLimit;
    private double commissionOverride;

    private String refCode;
    private String accountOpenDate;

    private boolean autoApprove;
    private String dividendDistribution;
    private boolean directCashSettlement;

    private String clearingAccountNo;

    private double maxOpenBuyLimit;
    private double maxDailyBuyLimit;

    private double cashInterestRate;

    private String custodianId;
    private String custodianLabel;

    private String agentId;
    private String agentLabel;

    private String csdSyncStatus;
    private String csdSyncMessage;

    private List<TradingAccount> tradingAccounts;

    private String marginExpirationDate;
    private String cashInterestStartDate;
    private String cashInterestEndDate;

    private List<ProductRequest> productRequests;

    private String groupId;
    private String productId;
    private String branchId;

    private String productCode;
    private String productLabel;

    private String glAccountId;

    private boolean marginAvailable;
    private double effectiveCommissionRate;

    private boolean productCsdIntegrated;

    private String branchCode;
    private String branchLabel;

    private String groupLabel;
    private String groupCode;

    // Nested DTO 1
    @Data
    public static class TradingAccount {
        private String id;
        private String tradingAccountLabel;
        private String tradingAccountNo;
        private String clearingAccountNo;
    }

    // Nested DTO 2
    @Data
    public static class ProductRequest {
        private String productType;
        private String productCode;
    }
}
