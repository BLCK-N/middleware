package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BankResponse {

    @JsonProperty("Banks")
    private List<Bank> banks;

    @Data
    public static class Bank {
        @JsonProperty("InstitutionCode")
        private String institutionCode;

        @JsonProperty("InstitutionName")
        private String institutionName;
    }
}