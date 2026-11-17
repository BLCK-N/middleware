package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiBalanceResponse {

    @JsonProperty("AccountName")
    private String accountName;

    @JsonProperty("AccountNumber")
    private String accountNumber;

    @JsonProperty("Balance")
    private double balance;

    @JsonProperty("BankVerificationNumber")
    private String bankVerificationNumber;

    @JsonProperty("ResponseCode")
    private String responseCode;

    @JsonProperty("ResponseMessage")
    private String responseMessage;
}
