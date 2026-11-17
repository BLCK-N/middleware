package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountResponse {

    @JsonProperty("AccountName")
    private String accountName;

    @JsonProperty("BankVerificationNumber")
    private String bankVerificationNumber;

    @JsonProperty("ResponseCode")
    private String responseCode;

    @JsonProperty("SessionID")
    private String sessionId;
}
