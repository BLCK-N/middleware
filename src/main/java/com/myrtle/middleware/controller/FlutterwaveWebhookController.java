package com.myrtle.middleware.controller;

import com.myrtle.middleware.configuration.FlutterwaveSecretsConfig;
import com.myrtle.middleware.dtos.request.FunctionResponse;
import com.myrtle.middleware.services.payment.FlutterwavePaymentService;
import com.myrtle.middleware.utils.FlutterwaveWebhookValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Flutterwave Webhook Controller
 * 
 * Handles webhook events from Flutterwave payment gateway
 * 
 * Security:
 * - Validates webhook signature using HMAC-SHA256
 * - Rejects requests with invalid signatures
 * - Processes webhooks idempotently
 * 
 * Requirements:
 * - Configure FLUTTERWAVE_WEBHOOK_SECRET in application properties
 * - Webhook URL must be publicly accessible
 */
@RestController
@RequestMapping("/api/v1/payments/flutterwave")
@Slf4j
@RequiredArgsConstructor
public class FlutterwaveWebhookController {

    @Lazy
    private final FlutterwavePaymentService flutterwavePaymentService;
    private final FlutterwaveWebhookValidator webhookValidator;
    private final FlutterwaveSecretsConfig secretsConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Webhook endpoint for Flutterwave payment notifications
     * 
     * Required query parameters:
     * - tx_ref: Transaction reference
     * - transaction_id: Flutterwave transaction ID
     * 
     * Required headers:
     * - verif-hash: HMAC-SHA256 signature of webhook payload
     * 
     * @param txRef Transaction reference from pending payment
     * @param transactionId Flutterwave transaction ID
     * @param verifHash HMAC-SHA256 signature header
     * @param request HTTP request to extract raw body
     * @return 200 OK if webhook processed successfully, 4xx/5xx on error
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleFlutterwaveWebhook(
            @RequestParam(name = "tx_ref") String txRef,
            @RequestParam(name = "transaction_id") String transactionId,
            @RequestHeader(name = "verif-hash", required = false) String verifHash,
            @RequestBody(required = false) Map<String, Object> payload) {

        log.info("Received Flutterwave webhook - txRef: {}, transactionId: {}", txRef, transactionId);

        try {
            // 1. Validate webhook secret is configured
            String webhookSecret = secretsConfig.getWebhookSecret();
            if (webhookSecret == null || webhookSecret.isEmpty()) {
                log.error("FLUTTERWAVE_WEBHOOK_SECRET is not configured");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Webhook secret not configured"));
            }

            // 2. Validate webhook signature (verif-hash)
            if (verifHash == null || verifHash.isEmpty()) {
                log.warn("Webhook rejected - missing verif-hash header");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Missing verif-hash header"));
            }

            // Reconstruct payload for signature validation
            String payloadString = payload != null ? objectMapper.writeValueAsString(payload) : "{}";

            boolean isValidSignature = webhookValidator.validateWebhookSignature(
                    payloadString,
                    verifHash,
                    webhookSecret
            );

            if (!isValidSignature) {
                log.warn("Webhook rejected - invalid signature for txRef: {}", txRef);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid webhook signature"));
            }

            log.info("Webhook signature validated successfully for txRef: {}", txRef);

            // 3. Extract payment status from webhook payload
            String status = payload != null ? (String) payload.get("status") : null;

            if (status == null) {
                log.warn("Webhook missing status field - txRef: {}", txRef);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Missing status in webhook payload"));
            }

            // 4. Only process successful payments
            if (!"successful".equalsIgnoreCase(status)) {
                log.info("Webhook received with status '{}' (not successful) - txRef: {}", status, txRef);
                // Still return 200 OK as we processed the webhook correctly
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Webhook processed - status: " + status,
                        "status", status
                ));
            }

            log.info("Processing successful payment through webhook - txRef: {}", txRef);

            // 5. Trigger verification flow (same as redirect callback)
            FunctionResponse verificationResult = flutterwavePaymentService.verifyPayment(
                    transactionId,
                    txRef
            );

            if (!verificationResult.isSuccess()) {
                log.warn("Webhook verification failed - txRef: {}, message: {}", 
                        txRef, verificationResult.message());
                // Return 200 OK (webhook processed) even if verification failed
                // The payment status is persisted in the database
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", verificationResult.message(),
                        "status", status
                ));
            }

            log.info("Webhook payment verification successful - txRef: {}", txRef);

            // 6. Return success response
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment verified and processed successfully",
                    "status", status
            ));

        } catch (Exception e) {
            log.error("Unexpected error processing Flutterwave webhook - txRef: {}", txRef, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Webhook processing failed: " + e.getMessage()));
        }
    }
}
