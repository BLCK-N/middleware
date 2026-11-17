package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SettlementBankPatch {

    private String id;
    private String type;

    private String stlBankName;
    private String stlBankRoutingNo; // ⚠️ String safer
    private String stlBankSwiftCode;

    private String stlBankBranch;
    private String stlBankAddress; // ⚠️ was number → String

    private String stlBankAccountName;
    private String stlBankAccountNo; // ⚠️ NEVER Long (can overflow)

    private String stlBankAccountOpenDate; // yyyy-MM-dd
    private String currency;
}
