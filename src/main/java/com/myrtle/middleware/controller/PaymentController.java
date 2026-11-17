package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.TransactionFilterRequest;
import com.myrtle.middleware.model.WithdrawalAccount;
import com.myrtle.middleware.repository.InvestmentRepository;
import com.myrtle.middleware.repository.PendingPaymentRepository;
import com.myrtle.middleware.repository.ProductRespository;
import com.myrtle.middleware.dtos.request.AddBankAccountRequest;
import com.myrtle.middleware.dtos.request.BankAccountRequest;
import com.myrtle.middleware.dtos.request.FunctionResponse;
import com.myrtle.middleware.dtos.request.TransferOutRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.investment.InvestmentService;
import com.myrtle.middleware.services.payment.FlutterwavePaymentService;
import com.myrtle.middleware.services.payment.PaymentService;
import com.myrtle.middleware.services.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@Controller
@RestController
@RequestMapping(path = "api/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

    private final FlutterwavePaymentService flutterwavePaymentService;
    private final ProductRespository productRespository;
    private final WalletService walletService;
    private final PaymentService paymentService;
    private final InvestmentService investmentService;
    private final InvestmentRepository investmentRepository;
    private final PendingPaymentRepository pendingPaymentRepository;

    @Value("${frontend.url}")
    String frontendUrl;

    /**
     * Flutterwave redirect callback handler
     * 
     * IMPORTANT: Do NOT trust the status query parameter from Flutterwave redirect
     * Always call Flutterwave /transactions/{id}/verify endpoint for server-side verification
     * 
     * Security: Only server verification determines payment success
     * 
     * @param status Query parameter (UNTRUSTED - do not use for verification)
     * @param txRef Transaction reference
     * @param transactionId Flutterwave transaction ID
     * @return Redirect to appropriate success or failure page
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> handlePaymentRedirect(
            @RequestParam String status,
            @RequestParam("tx_ref") String txRef,
            @RequestParam("transaction_id") String transactionId) {

        log.info("Received Flutterwave redirect callback - txRef: {}, transactionId: {}, status (untrusted): {}", 
                 txRef, transactionId, status);

        // NOTE: status parameter from redirect is UNTRUSTED and not used for verification
        // We always call verifyPayment() which performs server-side verification via Flutterwave API
        
        // 1. Verify payment via server-side API call (strict validation)
        FunctionResponse functionResponse = flutterwavePaymentService.verifyPayment(transactionId, txRef);

        boolean isSuccess = functionResponse.isSuccess();
        log.info("Payment verification completed - success: {}, message: {}", isSuccess, functionResponse.message());

        // 2. Extract relevant info from response data
        String investmentId = "";
        String productType = "NA";
        String amount = "0";
        String channel = "WEB"; // Default to WEB

        if (functionResponse.data() != null) {
            if (functionResponse.data() instanceof String) {
                // Legacy: investment ID as string
                investmentId = (String) functionResponse.data();
            } else if (functionResponse.data() instanceof Map) {
                // New format: structured data
                Map<String, Object> data = (Map<String, Object>) functionResponse.data();
                investmentId = (String) data.getOrDefault("investmentId", "");
                productType = (String) data.getOrDefault("productType", "NA");
                amount = (String) data.getOrDefault("amount", "0");
                channel = (String) data.getOrDefault("channel", "WEB");
            }
        }

        // 3. Determine if WEB or MOBILE based on channel from verified pending payment
        boolean isWeb = !"MOBILE".equalsIgnoreCase(channel);

        // 4. Build redirect URL based on verification result
        String redirectUrl;
        if (isWeb) {
            redirectUrl = isSuccess
                    ? String.format(
                    frontendUrl + "/dashboard/invest/payment-success?amount=%s&id=%s&type=%s",
                    amount,
                    investmentId,
                    productType
            )
                    : String.format(
                    frontendUrl + "/dashboard/invest/payment-failure?amount=%s&id=%s&type=%s",
                    amount,
                    investmentId,
                    productType
            );
        } else {
            // MOBILE ONLY
            redirectUrl = String.format(
                    "myrtlefinancialgroup://payment?amount=%s&id=%s&type=%s&status=%s",
                    amount,
                    investmentId,
                    productType,
                    isSuccess ? "success" : "failed"
            );
        }

        log.info("Redirecting user to: {} (success: {})", 
                 isSuccess ? "success page" : "failure page", isSuccess);

        // 5. Redirect to appropriate page
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }




    /**
     * Wallet balance inquiry (secured)
     */
//    @GetMapping("/get-balance")
//    @PreAuthorize("isAuthenticated()")
//    public ResponseEntity<ApiResponse> balanceInquiry(@AuthenticationPrincipal UserDetails userDetails) {
//        return walletService.balanceEnquiry(userDetails.getUsername());
//    }

    @GetMapping("/get-transactions/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> fetchTransactionById(@PathVariable String id, @AuthenticationPrincipal UserDetails userDetails) {
        return paymentService.fetchTransactionById(userDetails.getUsername(), id);
    }

    @GetMapping("/get-transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> fetchAllTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @ModelAttribute TransactionFilterRequest filter,
            Pageable pageable // Spring will auto-bind ?page=0&size=10&sort=createdAt,desc
    ) {
        return paymentService.fetchTransactions(userDetails.getUsername(), filter, pageable);
    }



    @GetMapping("/get-stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getStats(@AuthenticationPrincipal UserDetails userDetails) {
        return paymentService.getStats(userDetails.getUsername());
    }

    @GetMapping("/get-banks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> fetchAllBanks(@AuthenticationPrincipal UserDetails userDetails) {
        return paymentService.getBanks();
    }

    @PostMapping("/resolve-details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> resolveAccountDetails(@RequestBody BankAccountRequest accountRequest, @AuthenticationPrincipal UserDetails userDetails) {
        return walletService.nameInquiry(userDetails.getUsername(), accountRequest);
    }

    @PostMapping("/add-withdrawal-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> addAccountDetails(@RequestBody WithdrawalAccount accountRequest, @AuthenticationPrincipal UserDetails userDetails) {
        return walletService.addWithdrawalAccount(userDetails.getUsername(), accountRequest);
    }

    @GetMapping("/withdrawal-accounts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> fetchAccounts(@AuthenticationPrincipal UserDetails userDetails) {
        return walletService.fetchWithdrawalAccountsForUser(userDetails.getUsername());
    }

    @PutMapping("/edit-withdrawal-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> editAccount(
            @RequestBody WithdrawalAccount accountRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        return walletService.editWithdrawalAccount(userDetails.getUsername(), accountRequest);
    }

    @DeleteMapping("/delete-withdrawal-account/{accountId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> deleteAccount(
            @PathVariable String accountId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return walletService.deleteWithdrawalAccount(userDetails.getUsername(), accountId);
    }
//
//    @PostMapping("/transfer-out")
//    @PreAuthorize("isAuthenticated()")
//    public ResponseEntity<ApiResponse> transferMoneyOut(@RequestBody TransferOutRequest accountRequest, @AuthenticationPrincipal UserDetails userDetails) {
//        return walletService.withdrawalFromAccountExternal(userDetails.getUsername(), accountRequest.accountId(), accountRequest.amount());
//    }

//    @PostMapping("/add-bank-main")
//    @PreAuthorize("isAuthenticated()")
//    public ResponseEntity<ApiResponse> addGeneralBankAccount(@RequestBody AddBankAccountRequest accountRequest, @AuthenticationPrincipal UserDetails userDetails) {
//        return paymentService.addGeneralBankAccount(accountRequest);
//    }
//
//    @GetMapping("/get-settlement-banks")
//    public ResponseEntity<ApiResponse> getGeneralBanks() {
//        return paymentService.fetchGeneralAccounts();
//    }

}
