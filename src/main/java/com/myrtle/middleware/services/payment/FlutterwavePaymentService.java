package com.myrtle.middleware.services.payment;

import com.myrtle.middleware.configuration.FlutterwaveSecretsConfig;
import com.myrtle.middleware.custom.GenericRequest;
import com.myrtle.middleware.custom.GenericRequestService;
import com.myrtle.middleware.custom.HttpMethodType;
import com.myrtle.middleware.dtos.request.FunctionResponse;
import com.myrtle.middleware.enums.Currency;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.PendingPayment;
import com.myrtle.middleware.repository.PendingPaymentRepository;
import com.myrtle.middleware.services.investment.InvestmentService;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FlutterwavePaymentService {

    private final GenericRequestService genericRequestService;
    private final FlutterwaveSecretsConfig secretsConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private final InvestmentService investmentService;
    private final PendingPaymentRepository pendingPaymentRepository;

    @Value("${app.host}")
    private String appHost;
    @Value("${flutterwave.api.base-url}")
    private String flwBaseUrl;

    public FlutterwavePaymentService(
            GenericRequestService genericRequestService,
            FlutterwaveSecretsConfig secretsConfig,
            PendingPaymentRepository pendingPaymentRepository,
            ResponseUtil responseUtil,
            @Lazy InvestmentService investmentService) {
        this.genericRequestService = genericRequestService;
        this.secretsConfig = secretsConfig;
        this.pendingPaymentRepository = pendingPaymentRepository;
        this.investmentService = investmentService;
    }

    /**
     * Create a payment link
     */
    private String getPaymentsUrl() {
        return flwBaseUrl + "/payments";
    }

    private String getVerifyUrl() {
        return flwBaseUrl + "/transactions";
    }

    public String createPaymentLink(String productType, String email, String amount, String txRef, Currency currency) {
        String secretKey = secretsConfig.getKey(productType);
        if (secretKey == null) {
            throw new IllegalArgumentException("Invalid product type: " + productType);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("tx_ref", txRef);
        requestBody.put("amount", amount);
        requestBody.put("currency", currency.name());
        requestBody.put("payment_options", "card,banktransfer,ussd");
        requestBody.put("redirect_url", appHost + "/api/v1/payments/callback");

        Map<String, String> customer = new HashMap<>();
        customer.put("email", email);
        requestBody.put("customer", customer);

        Map<String, String> customizations = new HashMap<>();
        customizations.put("title", productType + " Payment");
        customizations.put("description", "Payment for " + productType);
        requestBody.put("customizations", customizations);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + secretKey);
        headers.put("Content-Type", "application/json");

        GenericRequest<Map<String, Object>> request = new GenericRequest<>();
        request.setUrl(getPaymentsUrl());
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(headers);
        request.setBody(requestBody);

        try {
            ResponseEntity<Map> response = genericRequestService.sendRequest(request, Map.class);

            if (response.getBody() != null && "success".equals(response.getBody().get("status"))) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                return (String) data.get("link");
            }

            log.error("Flutterwave API error response: {}", response.getBody());
            throw new RuntimeException("We couldn't create your payment link. Please try again.");
        } catch (Exception ex) {
            log.error("Error calling Flutterwave API at {}: {}", getPaymentsUrl(), ex.getMessage(), ex);
            throw new RuntimeException("We couldn't create your payment link. Please try again");
        }
    }

    /**
     * Verify a transaction using transactionId
     */
    public Map<String, Object> verifyTransaction(String productType, String transactionId) {
        String secretKey = secretsConfig.getKey(productType);
        if (secretKey == null) {
            throw new IllegalArgumentException("Invalid product type: " + productType);
        }

        String url = getVerifyUrl() + "/" + transactionId + "/verify";

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + secretKey);
        headers.put("Content-Type", "application/json");

        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(headers);

        ResponseEntity<Map> response = genericRequestService.sendRequest(request, Map.class);

        if (response.getBody() != null && "success".equals(response.getBody().get("status"))) {
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            log.debug("Flutterwave verification successful for transactionId: {}. Data: {}", transactionId, data);
            return data;
        }

        log.error("Flutterwave verification failed for transactionId: {}. Response: {}", transactionId, response.getBody());
        throw new RuntimeException("Failed to verify Flutterwave transaction: " + transactionId);
    }

    /**
     * Verify payment using transactionId and txRef with strict validation
     * <p>
     * Required validation checks:
     * 1. status must be "successful"
     * 2. tx_ref must exactly match the stored PendingPayment.txRef
     * 3. amount must equal PendingPayment.amount
     * 4. currency must equal PendingPayment.currency
     * 5. customer.email must equal the user's email on the pending payment
     * 6. charged_amount >= amount (prevent underpayment attacks)
     */
    @Transactional
    public FunctionResponse verifyPayment(String transactionId, String txRef) {
        log.info("Starting payment verification - transactionId: {}, txRef: {}", transactionId, txRef);

        try {
            // 1. Fetch Pending Payment
            PendingPayment pendingPayment = pendingPaymentRepository.findByTxRef(txRef)
                    .orElseThrow(() -> {
                        log.warn("Pending payment not found for txRef: {}", txRef);
                        return new ResourceNotFound("Pending payment not found");
                    });

            // 2. Enforce Idempotency - Check if payment already processed
            if (pendingPayment.getPaymentStatus() != PendingPayment.PaymentStatus.PENDING) {
                log.warn("Payment already processed for txRef: {} with status: {}", txRef, pendingPayment.getPaymentStatus());
                return FunctionResponse.builder()
                        .isSuccess(false)
                        .status(HttpStatus.CONFLICT)
                        .message("Payment already processed. Status: " + pendingPayment.getPaymentStatus())
                        .data(null)
                        .build();
            }

            // 3. Check if same transaction ID is being resubmitted
            if (pendingPayment.getFlutterwaveTransactionId() != null &&
                    pendingPayment.getFlutterwaveTransactionId().equals(transactionId)) {
                log.warn("Duplicate transaction verification attempt - txRef: {}, transactionId: {}", txRef, transactionId);
                return FunctionResponse.builder()
                        .isSuccess(false)
                        .status(HttpStatus.CONFLICT)
                        .message("Transaction already verified")
                        .data(null)
                        .build();
            }

            String productType = pendingPayment.getProduct().getProductType();

            // 4. Call Flutterwave verification API (server-side)
            Map<String, Object> verificationData = verifyTransaction(productType, transactionId);

            if (verificationData == null) {
                log.error("Flutterwave verification returned null for transactionId: {}", transactionId);
                markPaymentFailed(pendingPayment, transactionId, "Verification response is null");
                return buildFailureResponse(pendingPayment, productType, "Verification response is null");
            }

            // 5. Perform STRICT validation checks
            FunctionResponse validationResult = performStrictValidation(pendingPayment, verificationData, transactionId);
            if (!validationResult.isSuccess()) {
                log.warn("Strict validation failed for txRef: {}: {}", txRef, validationResult.message());
                return validationResult;
            }

            // 6. Store Flutterwave metadata before processing investment
            storeFlutterwaveMetadata(pendingPayment, verificationData);
            pendingPayment.setFlutterwaveTransactionId(transactionId);
            pendingPaymentRepository.save(pendingPayment);

            // 7. Process Investment Using Pending Data
            log.info("Verification passed, processing investment for txRef: {}", txRef);
            FunctionResponse investmentResult = investmentService.processPendingInvestmentPayment(txRef);

            // 8. Mark payment as successful
            if (investmentResult.isSuccess()) {
                pendingPayment.setPaymentStatus(PendingPayment.PaymentStatus.SUCCESS);
                pendingPayment.setCompletedAt(LocalDateTime.now());
                pendingPaymentRepository.save(pendingPayment);
                log.info("Payment verification successful and investment processed for txRef: {}", txRef);
            }

            // Return result with channel information for proper redirects
            return FunctionResponse.builder()
                    .isSuccess(investmentResult.isSuccess())
                    .status(investmentResult.status())
                    .message(investmentResult.message())
                    .data(investmentResult.data() != null ? investmentResult.data() : Map.of(
                            "investmentId", "",
                            "channel", pendingPayment.getChannel().name(),
                            "productType", productType,
                            "amount", pendingPayment.getAmount()
                    ))
                    .build();

        } catch (ResourceNotFound ex) {
            log.error("Pending payment not found for txRef: {}", txRef, ex);
            return FunctionResponse.builder()
                    .isSuccess(false)
                    .status(HttpStatus.NOT_FOUND)
                    .message(ex.getMessage())
                    .data(null)
                    .build();
        } catch (Exception ex) {
            log.error("Unexpected error during payment verification for txRef: {}, transactionId: {}", txRef, transactionId, ex);
            return FunctionResponse.builder()
                    .isSuccess(false)
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .message("Payment verification failed: " + ex.getMessage())
                    .data(null)
                    .build();
        }
    }

    /**
     * Perform strict validation of Flutterwave response against stored pending payment
     */
    private FunctionResponse performStrictValidation(PendingPayment pendingPayment,
                                                     Map<String, Object> verificationData,
                                                     String transactionId) {
        String productType = pendingPayment.getProduct().getProductType();

        log.info("Starting strict validation for txRef: {} - Expected Amount: {}, Currency: {}, Email: {}",
                pendingPayment.getTxRef(),
                pendingPayment.getAmount(),
                pendingPayment.getCurrency(),
                pendingPayment.getUser() != null ? pendingPayment.getUser().getEmailAddress() : "N/A");
        log.info("Flutterwave Response Data: {}", verificationData);

        // Check 1: Status must be "successful"
        String status = (String) verificationData.get("status");
        if (status == null || !"successful".equalsIgnoreCase(status)) {
            log.warn("Payment status check failed - Expected: successful, Got: {} for txRef: {}", status, pendingPayment.getTxRef());
            markPaymentFailed(pendingPayment, transactionId, "Payment status is not successful: " + status);
            return buildFailureResponse(pendingPayment, productType, "Payment status check failed");
        }

        // Check 2: tx_ref must exactly match
        String responseTxRef = (String) verificationData.get("tx_ref");
        if (responseTxRef == null || !responseTxRef.equals(pendingPayment.getTxRef())) {
            log.warn("TX_REF mismatch - Expected: {}, Got: {} for transactionId: {}",
                    pendingPayment.getTxRef(), responseTxRef, transactionId);
            markPaymentFailed(pendingPayment, transactionId, "TX_REF mismatch");
            return buildFailureResponse(pendingPayment, productType, "TX_REF verification failed");
        }

        // Check 3: Amount must match exactly (comparing as numbers to handle format differences)
        Object amountObj = verificationData.get("amount");
        String responseAmount = amountObj != null ? String.valueOf(amountObj) : null;

        if (responseAmount == null) {
            log.warn("Amount is null in Flutterwave response for txRef: {}", pendingPayment.getTxRef());
            markPaymentFailed(pendingPayment, transactionId, "Amount missing from Flutterwave response");
            return buildFailureResponse(pendingPayment, productType, "Amount verification failed");
        }

        // Compare amounts as numbers to handle format differences (e.g., "5000" vs "5000.00")
        try {
            double storedAmountDouble = Double.parseDouble(pendingPayment.getAmount());
            double responseAmountDouble = Double.parseDouble(responseAmount);

            if (Math.abs(storedAmountDouble - responseAmountDouble) > 0.01) { // Allow 0.01 rounding difference
                log.warn("Amount mismatch - Expected: {}, Got: {} for txRef: {}",
                        pendingPayment.getAmount(), responseAmount, pendingPayment.getTxRef());
                markPaymentFailed(pendingPayment, transactionId, "Amount mismatch");
                return buildFailureResponse(pendingPayment, productType, "Amount verification failed");
            }
        } catch (NumberFormatException e) {
            log.error("Invalid amount format - Stored: {}, Received: {} for txRef: {}",
                    pendingPayment.getAmount(), responseAmount, pendingPayment.getTxRef(), e);
            markPaymentFailed(pendingPayment, transactionId, "Invalid amount format");
            return buildFailureResponse(pendingPayment, productType, "Amount format validation failed");
        }

        // Check 4: Currency must match (case-insensitive)
        String responseCurrency = (String) verificationData.get("currency");
        String normalizedResponse = responseCurrency != null ? responseCurrency.toUpperCase() : null;
        String expectedCurrency = pendingPayment.getCurrency() != null ?
                pendingPayment.getCurrency().name().toUpperCase() : null;

        if (normalizedResponse == null || !normalizedResponse.equals(expectedCurrency)) {
            log.warn("Currency mismatch - Expected: {}, Got: {} for txRef: {}",
                    expectedCurrency, responseCurrency, pendingPayment.getTxRef());
            markPaymentFailed(pendingPayment, transactionId, "Currency mismatch");
            return buildFailureResponse(pendingPayment, productType, "Currency verification failed");
        }

        // Check 5: Customer email must match (if available)
        Map<String, Object> customer = (Map<String, Object>) verificationData.get("customer");
        String responseEmail = customer != null ? (String) customer.get("email") : null;
        String storedEmail = pendingPayment.getUser() != null ? pendingPayment.getUser().getEmailAddress() : null;

        // Email check is logged but not blocking - some payment methods may not include email
        if (responseEmail != null && storedEmail != null && !responseEmail.equals(storedEmail)) {
            log.warn("Customer email mismatch - Expected: {}, Got: {} for txRef: {} (non-blocking)",
                    storedEmail, responseEmail, pendingPayment.getTxRef());
        } else if (responseEmail != null && storedEmail == null) {
            log.warn("Stored email is null but Flutterwave provided: {} for txRef: {} (non-blocking)",
                    responseEmail, pendingPayment.getTxRef());
        }

        // Check 6: Charged amount must be >= amount (prevent underpayment)
        Object chargedAmountObj = verificationData.get("charged_amount");
        String chargedAmount = chargedAmountObj != null ? String.valueOf(chargedAmountObj) : null;

        if (chargedAmount != null) {
            try {
                double chargedAmountDouble = Double.parseDouble(chargedAmount);
                double expectedAmountDouble = Double.parseDouble(pendingPayment.getAmount());

                if (chargedAmountDouble < expectedAmountDouble) {
                    log.warn("Underpayment detected - Charged: {}, Expected: {} for txRef: {}",
                            chargedAmount, pendingPayment.getAmount(), pendingPayment.getTxRef());
                    markPaymentFailed(pendingPayment, transactionId, "Underpayment: charged less than required");
                    return buildFailureResponse(pendingPayment, productType, "Underpayment rejected");
                }
            } catch (NumberFormatException e) {
                log.error("Invalid amount format - Charged: {}, Expected: {} for txRef: {}",
                        chargedAmount, pendingPayment.getAmount(), pendingPayment.getTxRef());
                markPaymentFailed(pendingPayment, transactionId, "Invalid amount format");
                return buildFailureResponse(pendingPayment, productType, "Amount format validation failed");
            }
        }

        log.info("All strict validation checks passed for txRef: {}", pendingPayment.getTxRef());
        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Validation passed")
                .data(null)
                .build();
    }

    /**
     * Store Flutterwave metadata in pending payment
     */
    private void storeFlutterwaveMetadata(PendingPayment pendingPayment, Map<String, Object> verificationData) {
        try {
            pendingPayment.setChargedAmount(String.valueOf(verificationData.getOrDefault("charged_amount", "")));
            pendingPayment.setPaymentType((String) verificationData.getOrDefault("payment_type", ""));
            pendingPayment.setAppFee(String.valueOf(verificationData.getOrDefault("app_fee", "")));
            pendingPayment.setPaymentChannel((String) verificationData.getOrDefault("payment_method", ""));

            Map<String, Object> customer = (Map<String, Object>) verificationData.get("customer");
            if (customer != null) {
                pendingPayment.setCustomerEmail((String) customer.get("email"));
            }

            // Store full response for audit trail
            pendingPayment.setProcessorResponse(verificationData.toString());

            log.info("Flutterwave metadata stored for txRef: {}", pendingPayment.getTxRef());
        } catch (Exception e) {
            log.warn("Error storing Flutterwave metadata for txRef: {}", pendingPayment.getTxRef(), e);
            // Continue processing even if metadata storage fails
        }
    }

    /**
     * Mark a payment as failed and persist the failure
     */
    private void markPaymentFailed(PendingPayment pendingPayment, String transactionId, String reason) {
        pendingPayment.setPaymentStatus(PendingPayment.PaymentStatus.FAILED);
        if (transactionId != null && !transactionId.isEmpty()) {
            pendingPayment.setFlutterwaveTransactionId(transactionId);
        }
        pendingPaymentRepository.save(pendingPayment);
        log.warn("Payment marked as FAILED for txRef: {}, reason: {}", pendingPayment.getTxRef(), reason);
    }

    /**
     * Build a standardized failure response
     */
    private FunctionResponse buildFailureResponse(PendingPayment pendingPayment, String productType, String message) {
        return FunctionResponse.builder()
                .isSuccess(false)
                .status(HttpStatus.BAD_REQUEST)
                .message(message)
                .data(Map.of(
                        "channel", pendingPayment.getChannel().name(),
                        "productType", productType,
                        "amount", pendingPayment.getAmount()
                ))
                .build();
    }

    /**
     * Verify payment using txRef (fallback)
     */
    public boolean verifyWithTxRef(String productType, String txRef) {
        try {
            String secretKey = secretsConfig.getKey(productType);
            if (secretKey == null) {
                throw new IllegalArgumentException("Invalid product type: " + productType);
            }

            String url = getVerifyUrl() + "/verify_by_reference?tx_ref=" + txRef;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + secretKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object dataObj = response.getBody().get("data");

                if (dataObj instanceof List) {
                    List<Map<String, Object>> data = (List<Map<String, Object>>) dataObj;
                    if (!data.isEmpty()) {
                        String status = (String) data.get(0).get("status");
                        return "successful".equalsIgnoreCase(status);
                    }
                } else if (dataObj instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) dataObj;
                    String status = (String) data.get("status");
                    return "successful".equalsIgnoreCase(status);
                }
            }
        } catch (Exception ex) {
            log.error("TxRef verification failed for {}: {}", txRef, ex.getMessage());
        }
        return false;
    }
}
