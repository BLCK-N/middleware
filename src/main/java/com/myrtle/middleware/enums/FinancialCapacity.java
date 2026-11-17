package com.myrtle.middleware.enums;

public enum FinancialCapacity {
    EMERGING_INVESTOR("Emerging Investor"),
    GROWING_INVESTOR("Growing Investor"),
    AFFLUENT_INVESTOR("Affluent Investor"),
    HIGH_NET_WORTH("High Net Worth Indicator"),
    PRIVATE_WEALTH_UHNI("Private Wealth / UHNI Indicator");

    private final String displayName;

    FinancialCapacity(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
