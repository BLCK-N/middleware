package com.myrtle.middleware.enums;

/**
 * Enum representing the type of account owner for withdrawal/settlement accounts.
 * Used to distinguish between normal users and corporate accounts.
 */
public enum AccountOwnerType {
    /**
     * Withdrawal account belongs to a normal user
     */
    USER,

    /**
     * Withdrawal account belongs to a corporate account
     */
    CORPORATE_ACCOUNT
}
