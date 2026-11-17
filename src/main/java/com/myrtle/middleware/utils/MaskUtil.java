package com.myrtle.middleware.utils;

import org.springframework.util.StringUtils;

/**
 * Masking for values that are read back to the client for confirmation only - a BVN on a review
 * screen, for example, where the user needs to recognise the value without it being re-exposed.
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /**
     * Replaces everything but the last {@code visible} characters with asterisks.
     * Returns {@code null} for blank input, so an uncollected field stays absent rather than
     * showing up as a row of stars.
     */
    public static String maskTail(String value, int visible) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= visible) {
            return "*".repeat(trimmed.length());
        }
        return "*".repeat(trimmed.length() - visible) + trimmed.substring(trimmed.length() - visible);
    }
}
