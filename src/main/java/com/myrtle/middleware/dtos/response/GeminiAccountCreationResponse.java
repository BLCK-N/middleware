package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiAccountCreationResponse {

    @JsonProperty("AccountName")
    private String accountName;

    @JsonProperty("AccountNumber")
    private String accountNumber;

    @JsonProperty("BankVerificationNumber")
    private String bankVerificationNumber;

    @JsonProperty("ResponseCode")
    private String responseCode;
}
