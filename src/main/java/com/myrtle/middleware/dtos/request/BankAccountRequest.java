package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountRequest {

    @JsonProperty("AccountNo")
    private String AccountNo;

    @JsonProperty("BankCode")
    private String BankCode;
}
