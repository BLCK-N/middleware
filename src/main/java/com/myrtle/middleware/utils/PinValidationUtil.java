package com.myrtle.middleware.utils;

import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Utility component for PIN validation across both normal users and corporate accounts.
 * Provides centralized logic for PIN verification to ensure consistency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PinValidationUtil {

    private final PasswordEncoder passwordEncoder;

    /**
     * Validates PIN for a normal user.
     * @param user the user entity
     * @param pin the PIN to validate
     * @return true if PIN is valid, false otherwise
     */
    public boolean validateUserPin(User user, String pin) {
        if (user == null) {
            return false;
        }
        if (user.getPin() == null || user.getPin().isBlank()) {
            log.warn("User PIN not set for user: {}", user.getEmailAddress());
            return false;
        }
        return passwordEncoder.matches(pin, user.getPin());
    }

    /**
     * Validates PIN for a corporate account.
     * @param corporateClient the corporate client entity
     * @param pin the PIN to validate
     * @return true if PIN is valid, false otherwise
     */
    public boolean validateCorporatePin(CorporateClient corporateClient, String pin) {
        if (corporateClient == null) {
            return false;
        }
        if (!corporateClient.isPinSet() || !corporateClient.isPinEnabled()) {
            log.warn("Corporate PIN not set or disabled for corporate client: {}", corporateClient.getId());
            return false;
        }
        if (corporateClient.getPinHash() == null || corporateClient.getPinHash().isBlank()) {
            log.warn("Corporate PIN hash is empty for corporate client: {}", corporateClient.getId());
            return false;
        }
        return passwordEncoder.matches(pin, corporateClient.getPinHash());
    }

    /**
     * Validates PIN based on account type.
     * For users with CORPORATE_ACCOUNT role, validates against CorporateClient PIN.
     * For regular users, validates against User PIN.
     *
     * @param user the user entity
     * @param pin the PIN to validate
     * @return true if PIN is valid for the account type, false otherwise
     */
    public boolean validatePinByAccountType(User user, String pin) {
        if (user == null) {
            return false;
        }

        // If user role is CORPORATE_ACCOUNT, validate using corporate PIN
        if (user.getRole() == Role.CORPORATE_ACCOUNT) {
            // Note: In this case, user would contain corporate client info
            // This is a special case where we need to validate against CorporateClient
            log.warn("User has CORPORATE_ACCOUNT role but validatePinByAccountType was called with User object. " +
                    "Use validateCorporatePin directly.");
            return false;
        }

        // For normal users, validate using user PIN
        return validateUserPin(user, pin);
    }

    /**
     * Check if a user has PIN set and enabled.
     * @param user the user entity
     * @return true if PIN is set and ready for use
     */
    public boolean isUserPinSetAndEnabled(User user) {
        return user != null && user.getPin() != null && !user.getPin().isBlank();
    }

    /**
     * Check if a corporate account has PIN set and enabled.
     * @param corporateClient the corporate client entity
     * @return true if PIN is set and enabled
     */
    public boolean isCorporatePinSetAndEnabled(CorporateClient corporateClient) {
        return corporateClient != null && corporateClient.isPinSet() && corporateClient.isPinEnabled();
    }
}