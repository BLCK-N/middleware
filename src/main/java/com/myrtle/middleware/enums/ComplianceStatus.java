package com.myrtle.middleware.enums;

public enum ComplianceStatus {
    STANDARD_REVIEW("Standard Review"),
    ENHANCED_REVIEW("Enhanced Review Required"),
    HOLD_BEFORE_EXECUTION("Hold Before Execution");

    private final String displayName;

    ComplianceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
