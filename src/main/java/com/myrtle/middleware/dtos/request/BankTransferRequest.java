package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BankTransferRequest {
    @JsonProperty("AccountNo")
    private String accountNo;

    @JsonProperty("Amount")
    private String amount;

    @JsonProperty("BeneficiaryAccountName")
    private String beneficiaryAccountName;

    @JsonProperty("BeneficiaryAccountNumber")
    private String beneficiaryAccountNumber;

    @JsonProperty("BeneficiaryBankVerificationNumber")
    private String beneficiaryBankVerificationNumber;

    @JsonProperty("DestinationInstitutionCode")
    private String destinationInstitutionCode;

    @JsonProperty("MerchantCode")
    private String merchantCode;

    @JsonProperty("NameEnquiryRef")
    private String nameEnquiryRef;

    @JsonProperty("Narration")
    private String narration;

    @JsonProperty("TrxnFee")
    private String trxnFee;

    @JsonProperty("TrxnReferenceNo")
    private String trxnReferenceNo;

}
