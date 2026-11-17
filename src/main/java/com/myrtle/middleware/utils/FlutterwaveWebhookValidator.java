package com.myrtle.middleware.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Utility class for validating Flutterwave webhooks
 * 
 * Validates the verif-hash header to ensure webhook authenticity
 */
@Slf4j
@Component
public class FlutterwaveWebhookValidator {

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * Validate webhook signature using HMAC-SHA256
     * 
     * @param payload The raw webhook payload as string
     * @param signature The verif-hash header value from webhook
     * @param webhookSecret The Flutterwave webhook secret
     * @return true if signature is valid, false otherwise
     */
    public boolean validateWebhookSignature(String payload, String signature, String webhookSecret) {
        try {
            if (payload == null || payload.isEmpty()) {
                log.warn("Webhook payload is null or empty");
                return false;
            }

            if (signature == null || signature.isEmpty()) {
                log.warn("Webhook signature (verif-hash) is missing");
                return false;
            }

            if (webhookSecret == null || webhookSecret.isEmpty()) {
                log.error("Webhook secret is not configured");
                return false;
            }

            // Generate expected hash
            String expectedHash = generateHash(payload, webhookSecret);

            // Compare hashes using constant-time comparison to prevent timing attacks
            boolean isValid = constantTimeEquals(signature, expectedHash);

            if (!isValid) {
                log.warn("Webhook signature validation failed. Expected: {}, Got: {}", 
                        expectedHash, signature);
            } else {
                log.debug("Webhook signature validation successful");
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error validating webhook signature", e);
            return false;
        }
    }

    /**
     * Generate HMAC-SHA256 hash for webhook payload
     * 
     * @param payload The webhook payload
     * @param secret The webhook secret
     * @return Hexadecimal hash string
     */
    private String generateHash(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                0,
                secret.length(),
                ALGORITHM
        );
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal, false otherwise
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }

        return result == 0;
    }
}
