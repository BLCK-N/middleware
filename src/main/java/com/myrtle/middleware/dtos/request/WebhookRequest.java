package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WebhookRequest {

    @JsonProperty("BeneficiaryAccountNo")
    private String beneficiaryAccountNo;

    @JsonProperty("BeneficiaryAccountName")
    private String beneficiaryAccountName;

    @JsonProperty("OriginatorAccountNo")
    private String originatorAccountNo;

    @JsonProperty("OriginatorAccountName")
    private String originatorAccountName;

    @JsonProperty("Amount")
    private String amount;

    @JsonProperty("Narration")
    private String narration;

    @JsonProperty("TrxnReferenceNo")
    private String trxnReferenceNo;

    @JsonProperty("SenderBankCode")
    private String senderBankCode;

    @JsonProperty("SenderBankName")
    private String senderBankName;

    @JsonProperty("TrxnDate")
    private String trxnDate;

    @JsonProperty("SessionID")
    private String sessionID;

}
