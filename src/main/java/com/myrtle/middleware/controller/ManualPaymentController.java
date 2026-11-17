package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.payment.ManualPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/manual-payment")
public class ManualPaymentController {

    private final ManualPaymentService manualPaymentService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/destination-account/{secId}")
    public ResponseEntity<ApiResponse> getDestinationAccount(@PathVariable String secId, @AuthenticationPrincipal UserDetails userDetails) {
        return manualPaymentService.findDestinationAccount(secId, userDetails);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("register-payment/{productId}")
    public ResponseEntity<ApiResponse> registerPaymentRequest(@PathVariable String productId, @AuthenticationPrincipal UserDetails userDetails) {
        return manualPaymentService.registerPayment(productId, userDetails);
    }

//    @PreAuthorize("isAuthenticated()")
//    @GetMapping("/get-status/{transactionId}")
//    public ResponseEntity<ApiResponse> getManualTxnStatus(@PathVariable String transactionId, @AuthenticationPrincipal UserDetails userDetails) {
//        return manualPaymentService.getManualTxnStatus(transactionId, userDetails);
//    }

//    @GetMapping("/transactions")
//    @PreAuthorize("isAuthenticated()")
//    public ResponseEntity<ApiResponse> getTransactionsByUserId(
//            @AuthenticationPrincipal UserDetails userDetails,
//            Pageable pageable) {
//        return manualPaymentService.getTransactionsByUserId(userDetails, pageable);
//    }
}
