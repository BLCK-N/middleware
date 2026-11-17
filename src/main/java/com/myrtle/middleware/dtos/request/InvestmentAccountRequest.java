package com.myrtle.middleware.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentAccountRequest {
    private String clientId;
    private String productId;
    private String branchId;

    private AccountUsage accountUsage;   // LIVE | PRACTICE
    private MgmtType mgmtType;           // SELF | ADVISORY | MANAGED | CUSTODIAN | RESERVED | PROP

    private String accountLabel;         // <= 255 characters
    private boolean autoApprove;
    private boolean directCashSettlement;

    public enum AccountUsage {
        LIVE, PRACTICE
    }

    public enum MgmtType {
        SELF, ADVISORY, MANAGED, CUSTODIAN, RESERVED, PROP
    }
}
