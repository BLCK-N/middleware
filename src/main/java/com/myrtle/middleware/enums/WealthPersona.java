package com.myrtle.middleware.enums;

public enum WealthPersona {
    EVERYDAY_BUILDER("Everyday Builder"),
    STRATEGIC_ACHIEVER("Strategic Achiever"),
    LEGACY_PRIVATE_WEALTH("Legacy / Private Wealth Client");

    private final String displayName;

    WealthPersona(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
