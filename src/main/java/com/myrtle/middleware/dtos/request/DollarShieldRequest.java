package com.myrtle.middleware.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DollarShieldRequest {

    // Minimal fields required for DollarShield email flow
    private String accountId;   // investmentAccountId from user profile
    private String productId;   // Product being referenced
    private String pin;         // User PIN for verification
    private String url;         // Attachment URL for email
    private String refCode;     // Reference Code

}
