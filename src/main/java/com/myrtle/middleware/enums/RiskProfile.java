package com.myrtle.middleware.enums;

/**
 * Ordinal order matters: it runs from least to most aggressive so cap rules
 * can be applied as min(bandedProfile, capMax).
 */
public enum RiskProfile {
    CONSERVATIVE("Conservative"),
    MODERATE("Moderate"),
    BALANCED("Balanced"),
    GROWTH_ORIENTED("Growth-Oriented"),
    AGGRESSIVE("Aggressive");

    private final String displayName;

    RiskProfile(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public RiskProfile cappedAt(RiskProfile max) {
        return this.ordinal() <= max.ordinal() ? this : max;
    }
}
