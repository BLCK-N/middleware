package com.myrtle.middleware.dtos.response;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClientReportResponse {

    private LocalDate reportDate;
    private String clientId;
    private String clientLabel;
    private String clientCode;
    private LocalDate positionDate;
    private String reportCurrency;

    private List<String> dayLabels;

    private List<BalanceByProductType> balanceByProductType;

    private Map<String, TransactionCount> transactionCount;

    private Map<String, List<AllocationHistoryItem>> allocationHistory;

    private RecentTransactions recentTransactions;

    private Map<String, AssetAllocationByCategory> assetAllocationByCategory;

    // -------------------- Nested Classes --------------------

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class BalanceByProductType {
        private LocalDate positionDate;
        private String productType;
        private double netChange;
        private double paymentDue;
        private double totalBalance;
        private double availableBalance;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TransactionCount {
        private int inflow;
        private int outflow;
        private int cash;
        private int transfer;
        private int card;
        private int billPay;
        private int teller;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class AllocationHistoryItem {
        private String name;
        private List<Double> data;
        private String type;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class RecentTransactions {
        private List<TransactionItem> older;
        private List<TransactionItem> today;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TransactionItem {
        private LocalDateTime time;
        private String type;
        private double amount;
        private String currency;
        private String description;
        private String account;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class AssetAllocationByCategory {
        private List<String> labels;
        private List<Double> value;
        private double totalCash;
        private double totalSecurities;
    }
}
