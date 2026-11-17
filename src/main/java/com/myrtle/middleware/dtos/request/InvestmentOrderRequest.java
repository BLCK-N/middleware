package com.myrtle.middleware.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentOrderRequest {

    private String accountId;          // {{invstAcctId}}
    private OrderType orderType;       // SUBSCRIPTION or REDEMPTION
    private String marketCode;         // {{marketCode}}
    private String secId;              // {{secId}}
    private String requestedQty;      // Either requestedQty or requestedVal
    private String requestedVal;       // Either requestedQty or requestedVal
    private String price;              // Optional
    private String requestDate;        // yyyy-MM-dd
    private Currency currency;         // NGN, USD, etc.
    private String notes;              // Optional
    private Boolean autoApprove;       // true / false
    private Boolean autoPayRedemption; // Required for REDEMPTION
    private Channel channel;           // NA, WEB, MOBILE, API
    private String refCode;            // Optional

    public enum OrderType {
        SUBSCRIPTION,
        REDEMPTION
    }

    public enum Channel {
        NA,
        WEB,
        MOBILE,
        API
    }

    public enum Currency {
        NGN,
        USD,
        EUR,
        GBP
        // Add more if your system supports them
    }
}
