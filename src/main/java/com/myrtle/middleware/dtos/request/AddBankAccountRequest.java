package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.Currency;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddBankAccountRequest {
    private String accountNumber;
    private String accountName;
    private Currency currency; // NGN / USD
    private String productName;
}

