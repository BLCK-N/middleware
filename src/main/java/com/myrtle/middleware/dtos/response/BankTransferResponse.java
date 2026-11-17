package com.myrtle.middleware.dtos.response;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankTransferResponse {

    @JsonProperty("Amount")
    private String amount;

    @JsonProperty("ResponseCode")
    private String responseCode;

    @JsonProperty("ResponseMessage")
    private String responseMessage;

    @JsonProperty("TrxnRefNo")
    private String trxnRefNo;
}