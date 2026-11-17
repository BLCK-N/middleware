package com.myrtle.middleware.enums;

public enum TransactionType {
    INVESTMENT_CREATION,
    INVESTMENT_TERMINATION,
    INVESTMENT_REDEMPTION,   // More professional than "withdrawal"
    WALLET_DEPOSIT,
    WALLET_WITHDRAWAL
}