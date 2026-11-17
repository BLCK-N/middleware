package com.myrtle.middleware.dtos.response;

/**
 * Minimal confirmation that the RC number + registered BVN matched a corporate account, plus the
 * short-lived token required to attach a new login to it.
 */
public record CorporateLookupResponse(
        String companyName,
        String rcNumber,
        String refCode,
        String maskedEmail,
        int existingUsers,
        int maxUsers,
        String lookupToken,
        long lookupTokenExpiresInSeconds
) {
}
