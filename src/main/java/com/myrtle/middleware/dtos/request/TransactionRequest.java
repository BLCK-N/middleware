package com.myrtle.middleware.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a transaction request for deposits, payments, or transfers.
 */
@Data                       // Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor          // No-args constructor
@AllArgsConstructor         // All-args constructor
public class TransactionRequest {

    private String accountId;           // * required
    private String branchId;            // * required
    private String destAccountId;       // optional (required if type == TRANSFER)
    private DestAccountType destAccountType; // optional (required if type == TRANSFER)
    private Channel channel;            // * required
    private TransactionType type;       // * required
    private double amount;              // * required >= 0.01
    private CurrencyCode currency;      // * required, 3-letter ISO
    private String reference;           // * required, <= 2000 chars
    private String notes;               // optional, <= 5000 chars
    private String valueDate;           // * required, ISO date yyyy-MM-dd
    private boolean autoApprove;        // * required
    private String transSrcAccountId;   // optional
    private String refCode;             // optional, <= 100 chars
    private boolean skipKycTierCheck;   // optional

    public enum Channel {
        CASH, CHEQUE, ELECTRONIC
    }

    public enum TransactionType {
        DEPOSIT, PAYMENT, TRANSFER,WITHDRAWAL
    }

    public enum DestAccountType {
        INVEST, DEPOSIT, INSURANCE, LOAN, NA, AGENT, CUSTODIAN
    }

    public enum CurrencyCode {
        NGN, USD, EUR, GBP, JPY // extendable with ISO 4217 set
    }
}
