package com.myrtle.middleware.dtos.request;

import lombok.Data;

@Data
public class LedgerStatementRequest {
    private String accountId;
    private String marketCode;
    private String secId;
    private String endDate;
    private String startDate;
}
