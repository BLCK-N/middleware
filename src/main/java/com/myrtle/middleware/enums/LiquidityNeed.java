package com.myrtle.middleware.enums;

public enum LiquidityNeed {
    HIGH("High Liquidity Need"),
    MODERATE("Moderate Liquidity Need"),
    LOW("Low Liquidity Need"),
    VERY_LOW("Very Low Liquidity Need");

    private final String displayName;

    LiquidityNeed(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
