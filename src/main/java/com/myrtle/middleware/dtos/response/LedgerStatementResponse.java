package com.myrtle.middleware.dtos.response;

import lombok.*;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LedgerStatementResponse {

    private String clientId;
    private String clientCode;
    private String clientLabel;
    private String accountId;
    private String accountNo;
    private String accountLabel;
    private String instrumentId;
    private String secId;
    private String secDesc;
    private String marketCode;
    private String startDate;
    private String endDate;
    private String currency;

    private double openingBalance;
    private double totalSubscription;
    private double totalRedemption;
    private double accruedInterest;
    private double cashBalance;
    private double closingBalance;
    private double currentPrice;
    private double closingValue;
    private double gainLoss;
    private double gainLossPercent;

    private List<StatementLine> statementLines;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatementLine {
        private String transDate;
        private String description;
        private String transType;
        private double transPrice;
        private double transAmount;
        private double transUnits;
        private double balance;
        private String transSource;
    }
}
