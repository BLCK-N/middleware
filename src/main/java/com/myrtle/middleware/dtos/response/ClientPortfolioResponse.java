package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientPortfolioResponse {

    private Client client;

    private List<Account> depositAccounts;
    private List<Account> loanAccounts;
    private List<Account> insuranceAccounts;
    private List<InvestmentAccount> investmentAccounts;

    private List<Position> topHoldings;
    private List<CashTransaction> cashTransactions;

    private String currency;
    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal totalCashBalance;
    private BigDecimal totalHeldAmount;
    private BigDecimal totalPositionBalance;
    private BigDecimal totalPositionCost;
    private BigDecimal currentAccountValue;
    private BigDecimal previousAccountValue;
    private BigDecimal changeFromLastPeriod;

    private BigDecimal totalCashInflow;
    private BigDecimal totalCashOutflow;
    private BigDecimal totalCashNetChange;
    private BigDecimal totalFees;

    /**
     * Rounds every {@link BigDecimal} in this response (including nested objects) to 2 decimal places
     * using {@link RoundingMode#HALF_DOWN}, in place. Safe to call on partially populated responses.
     */
    public void roundAllBigDecimals() {
        totalCashBalance = round2(totalCashBalance);
        totalHeldAmount = round2(totalHeldAmount);
        totalPositionBalance = round2(totalPositionBalance);
        totalPositionCost = round2(totalPositionCost);
        currentAccountValue = round2(currentAccountValue);
        previousAccountValue = round2(previousAccountValue);
        changeFromLastPeriod = round2(changeFromLastPeriod);
        totalCashInflow = round2(totalCashInflow);
        totalCashOutflow = round2(totalCashOutflow);
        totalCashNetChange = round2(totalCashNetChange);
        totalFees = round2(totalFees);

        if (investmentAccounts != null) {
            for (InvestmentAccount account : investmentAccounts) {
                if (account != null) {
                    account.roundBigDecimals();
                }
            }
        }
        if (topHoldings != null) {
            for (Position position : topHoldings) {
                if (position != null) {
                    position.roundBigDecimals();
                }
            }
        }
        if (cashTransactions != null) {
            for (CashTransaction tx : cashTransactions) {
                if (tx != null) {
                    tx.roundBigDecimals();
                }
            }
        }
    }

    private static BigDecimal round2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_DOWN);
    }

    /* ================= CLIENT ================= */

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Client {
        private String id;
        private String clientType;
        private String businessStructure;
        private String businessIncCode;
        private String status;
        private String code;
        private String label;
        private String notes;
        private String mobileNo;
        private String officeNo;
        private List<Address> address;
        private String valuationCurrency;
        private String email;
        private String notificationEmail;
        private String pictureUrl;
        private String kycTier;
    }

    @Data
    public static class Address {
        private String type;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postCode;
        private String country;
    }

    /* ================= ACCOUNTS ================= */

    @Data
    public static class Account {
        private String accountId;
        private String accountNo;
        private String status;
        private String currency;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InvestmentAccount {

        private String refCode;
        private String accountType;
        private String productType;
        private String productCode;
        private String productLabel;

        private String accountId;
        private String accountNo;
        private String accountLabel;
        private String tradingAccountNo;
        private String status;
        private String currency;
        private String branch;
        private String notes;

        private int securitiesBoughtReportPeriod;
        private int securitiesSoldReportPeriod;
        private int securitiesTransferredInReportPeriod;
        private int securitiesTransferredOutReportPeriod;

        private BigDecimal dividendsAndOtherIncomeReportPeriod;
        private BigDecimal transactionCostAndFeesReportPeriod;
        private BigDecimal changeFromLastPeriod;

        private BigDecimal totalAdditions;
        private BigDecimal totalSubtractions;

        private Valuation startingValuation;
        private Valuation endingValuation;

        private List<Position> positions;
        private List<Object> fixedDeposit;
        private List<Object> moneyMarket;

        private List<FundContract> fundContracts;
        private List<Object> orderContracts;

        private BigDecimal cashInflowReportPeriod;
        private BigDecimal cashOutflowReportPeriod;

        private List<Position> topHoldings;

        void roundBigDecimals() {
            dividendsAndOtherIncomeReportPeriod = round2(dividendsAndOtherIncomeReportPeriod);
            transactionCostAndFeesReportPeriod = round2(transactionCostAndFeesReportPeriod);
            changeFromLastPeriod = round2(changeFromLastPeriod);
            totalAdditions = round2(totalAdditions);
            totalSubtractions = round2(totalSubtractions);
            cashInflowReportPeriod = round2(cashInflowReportPeriod);
            cashOutflowReportPeriod = round2(cashOutflowReportPeriod);

            if (startingValuation != null) {
                startingValuation.roundBigDecimals();
            }
            if (endingValuation != null) {
                endingValuation.roundBigDecimals();
            }
            if (positions != null) {
                for (Position p : positions) {
                    if (p != null) {
                        p.roundBigDecimals();
                    }
                }
            }
            if (fundContracts != null) {
                for (FundContract fc : fundContracts) {
                    if (fc != null) {
                        fc.roundBigDecimals();
                    }
                }
            }
            if (topHoldings != null) {
                for (Position p : topHoldings) {
                    if (p != null) {
                        p.roundBigDecimals();
                    }
                }
            }
        }
    }

    /* ================= VALUATION ================= */

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Valuation {

        private String accountId;
        private LocalDate reportDate;

        private BigDecimal fundValue;
        private BigDecimal fundCost;
        private BigDecimal equityValue;
        private BigDecimal equityCost;
        private BigDecimal realEstateValue;
        private BigDecimal realEstateCost;
        private BigDecimal commodityValue;
        private BigDecimal commodityCost;
        private BigDecimal cryptoValue;
        private BigDecimal cryptoCost;
        private BigDecimal derivativeValue;
        private BigDecimal derivativeCost;
        private BigDecimal fixedIncomeValue;
        private BigDecimal fixedIncomeCost;
        private BigDecimal moneyMarketValue;
        private BigDecimal moneyMarketCost;
        private BigDecimal fixedDepositValue;
        private BigDecimal fixedDepositCost;
        private BigDecimal otherAssetsValue;
        private BigDecimal otherAssetsCost;

        private BigDecimal cashBalance;
        private BigDecimal cashInflow;
        private BigDecimal cashOutflow;

        private BigDecimal accruedMgmtFee;
        private BigDecimal mgmtFeeLiability;

        private BigDecimal grossAccountValue;
        private BigDecimal netAccountValue;

        private BigDecimal creditInterest;
        private BigDecimal creditInterestAsset;
        private BigDecimal debitInterest;
        private BigDecimal debitInterestLiability;

        private BigDecimal reportTotalCost;
        private BigDecimal reportTotalValue;

        private List<Object> fixedDepositTransactions;
        private List<Object> moneyMarketTransactions;

        private String updatedAt;

        private BigDecimal totalValue;
        private BigDecimal totalCost;

        void roundBigDecimals() {
            fundValue = round2(fundValue);
            fundCost = round2(fundCost);
            equityValue = round2(equityValue);
            equityCost = round2(equityCost);
            realEstateValue = round2(realEstateValue);
            realEstateCost = round2(realEstateCost);
            commodityValue = round2(commodityValue);
            commodityCost = round2(commodityCost);
            cryptoValue = round2(cryptoValue);
            cryptoCost = round2(cryptoCost);
            derivativeValue = round2(derivativeValue);
            derivativeCost = round2(derivativeCost);
            fixedIncomeValue = round2(fixedIncomeValue);
            fixedIncomeCost = round2(fixedIncomeCost);
            moneyMarketValue = round2(moneyMarketValue);
            moneyMarketCost = round2(moneyMarketCost);
            fixedDepositValue = round2(fixedDepositValue);
            fixedDepositCost = round2(fixedDepositCost);
            otherAssetsValue = round2(otherAssetsValue);
            otherAssetsCost = round2(otherAssetsCost);
            cashBalance = round2(cashBalance);
            cashInflow = round2(cashInflow);
            cashOutflow = round2(cashOutflow);
            accruedMgmtFee = round2(accruedMgmtFee);
            mgmtFeeLiability = round2(mgmtFeeLiability);
            grossAccountValue = round2(grossAccountValue);
            netAccountValue = round2(netAccountValue);
            creditInterest = round2(creditInterest);
            creditInterestAsset = round2(creditInterestAsset);
            debitInterest = round2(debitInterest);
            debitInterestLiability = round2(debitInterestLiability);
            reportTotalCost = round2(reportTotalCost);
            reportTotalValue = round2(reportTotalValue);
            totalValue = round2(totalValue);
            totalCost = round2(totalCost);
        }
    }

    /* ================= POSITIONS ================= */

    @Data
    public static class Position {

        private String marketId;
        private String secId;
        private String secDesc;
        private String instrumentType;

        private BigDecimal price;
        private BigDecimal quantity;
        private String currency;

        private BigDecimal positionCost;
        private BigDecimal positionValue;
        private BigDecimal reportValue;
        private BigDecimal portfolioPerc;

        private String accountId;
        private String accountNo;
        private String accountLabel;

        private BigDecimal gain;
        private BigDecimal gainPerc;
        private BigDecimal avgCost;
        private BigDecimal dividend;

        void roundBigDecimals() {
            price = round2(price);
            quantity = round2(quantity);
            positionCost = round2(positionCost);
            positionValue = round2(positionValue);
            reportValue = round2(reportValue);
            portfolioPerc = round2(portfolioPerc);
            gain = round2(gain);
            gainPerc = round2(gainPerc);
            avgCost = round2(avgCost);
            dividend = round2(dividend);
        }
    }

    /* ================= FUND CONTRACTS ================= */

    @Data
    public static class FundContract {

        private String marketCode;
        private String secId;
        private String orderType;
        private String status;
        private String orderNo;
        private String orderDesc;

        private BigDecimal contractQty;
        private BigDecimal contractPrice;
        private BigDecimal consideration;
        private BigDecimal commission;
        private BigDecimal fees;
        private BigDecimal penalty;
        private BigDecimal contractTotal;

        private LocalDate transactionDate;
        private LocalDate settlementDate;

        private String contractNo;
        private String fundType;

        void roundBigDecimals() {
            contractQty = round2(contractQty);
            contractPrice = round2(contractPrice);
            consideration = round2(consideration);
            commission = round2(commission);
            fees = round2(fees);
            penalty = round2(penalty);
            contractTotal = round2(contractTotal);
        }
    }

    /* ================= CASH TRANSACTIONS ================= */

    @Data
    public static class CashTransaction {

        private LocalDate transDate;
        private String type;
        private BigDecimal amount;
        private String currency;

        private String transNo;
        private String transDesc;
        private String accountNo;
        private String channel;

        void roundBigDecimals() {
            amount = round2(amount);
        }
    }
}

