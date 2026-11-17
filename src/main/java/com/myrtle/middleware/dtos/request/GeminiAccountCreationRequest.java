package com.myrtle.middleware.dtos.request;

public record GeminiAccountCreationRequest(
        String AccountName,
        String BVN,
        String Address,
        String City,
        String Email,
        String MobileNo,
        String NIN,
        String StateCode,
        String MerchantCode
        // String HashData // uncomment if you need to send hash later
) {
}
