package com.myrtle.middleware.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundTransactionResponse {

    // Required fields
    private String id;                  // UUID
    private String createdBy;
    private String createdAt;           // ISO datetime
    private String updatedBy;
    private String updatedAt;           // ISO datetime
    private Boolean deleted;
    private String marketCode;
    private String secId;
    private String clientId;            // UUID
    private String instrumentId;        // UUID
    private String clientCode;
    private String clientLabel;
    private OrderType orderType;
    private FundType fundType;
    private OrderStatus orderStatus;
    private String orderNo;
    private String orderDesc;
    private Double requestedQty;
    private Double requestedVal;
    private Double price;
    private String requestDate;         // yyyy-MM-dd
    private Currency currency;
    private String refCode;
    private Double consideration;
    private Double commission;
    private Double fees;
    private Double penalty;
    private Double avgCost;
    private Double totalValue;
    private Boolean autoApprove;
    private Boolean fullRedemption;
    private Double accruedDividend;
    private Channel channel;
    private String accountLabel;
    private String accountNo;
    private String accountId;           // UUID

    // Optional fields
    private String idemPotencyId;       // UUID
    private String notes;
    private Boolean autoPayRedemption;
    private String tradingAccountNo;
    private String tradingSubAccountNo;

    // --- Enums ---

    public enum OrderType {
        SUBSCRIPTION,
        REDEMPTION
    }

    public enum FundType {
        EQUITY,
        MONEY_MARKET,
        BOND,
        REAL_ESTATE,
        BALANCED,
        EXCHANGE_TRADED,
        ETHICAL,
        SECTOR,
        UMBRELLA
    }

    public enum OrderStatus {
        PENDING,
        APPROVED,
        FILLED,
        CANCELED,
        REVERSED
    }

    public enum Currency {
        USD,
        NGN,
        EUR,
        GBP
        // extend if needed
    }

    public enum Channel {
        NA,
        WEB,
        MOBILE,
        USSD,
        ATM,
        POS,
        BRANCH,
        EXEC_REPORT,
        FILE_UPLOAD,
        API,
        EMAIL,
        SMS,
        IVR,
        KIOSK,
        CALL_CENTER,
        OTHER
    }
}
