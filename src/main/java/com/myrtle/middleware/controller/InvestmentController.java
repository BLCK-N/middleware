package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.InvestmentFilterRequest;
import com.myrtle.middleware.dtos.request.*;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.TerminateRequest;
import com.myrtle.middleware.services.investment.InvestmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RestController
@RequestMapping(path = "api/v1/investment")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;

    private static ResponseEntity<ApiResponse> toResponse(FunctionResponse fr) {
        if (fr == null) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "An error occurred", null));
        }
        return ResponseEntity.status(fr.status()).body(new ApiResponse(fr.isSuccess(), fr.message(), fr.data()));
    }

    @PostMapping("/create-investment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> createInvestments(@RequestBody InvestmentPayload payload, @AuthenticationPrincipal UserDetails userDetails) {
        return investmentService.createInvestment(userDetails.getUsername(), payload);
    }

    @PostMapping("/terminate-investment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> terminateInvestment(
            @RequestBody TerminateRequest payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        return toResponse(investmentService.terminateInvestment(
                userDetails.getUsername(),
                payload.investmentId(),
                payload.pin(),
                payload.amount()
        ));
    }

    @PostMapping("/terminate-all-investments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> terminateAllInvestment(
            @RequestBody TerminateBulkPayload payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        return toResponse(investmentService.terminateAllInvestment(
                userDetails.getUsername(),
                payload.pin(),
                payload.productId()
        ));
    }

    @PostMapping("/withdrawal-investment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> withdrawalInvestment(
            @RequestBody WithdrawalInvestmentRequest payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        return toResponse(investmentService.withdrawalInvestment(userDetails.getUsername(), payload.pin(), payload.productId(), payload.amount()));
    }


    @PostMapping("/my-wealth-investment-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getInvestmentsRates(
            @RequestBody LedgerStatementRequest payload,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return toResponse(investmentService.getRates(userDetails.getUsername(), payload));
    }


    @GetMapping("/get-investments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getInvestments(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return toResponse(investmentService.fetchAllRunningInvestments(userDetails.getUsername(), page, size));
    }

    @GetMapping("/withdrawal-history/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getWithdrawalHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return toResponse(investmentService.getAllWithdrawalHistory(userDetails.getUsername(), productId, page, size));
    }

    @GetMapping("/get-investments-product/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getInvestmentsByProduct(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return toResponse(investmentService.fetchAllRunningInvestmentsByProduct(
                userDetails.getUsername(), productId, page, size));
    }

    @PostMapping("/get-real-rates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getRealRates(@RequestBody RealRateRequest realRateRequest, @AuthenticationPrincipal UserDetails userDetails) {
        return toResponse(investmentService.getRealRates(userDetails.getUsername(), realRateRequest));
    }

    @GetMapping("/get-asset-balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> fetchAssetBalance(@AuthenticationPrincipal UserDetails userDetails) {
        return toResponse(investmentService.getTotalInvestmentAmount(userDetails.getUsername()));
    }

    @PostMapping("/invest-dollar-shield")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> investInDollarShield(@RequestBody DollarShieldRequest realRateRequest, @AuthenticationPrincipal UserDetails userDetails) {
        return toResponse(investmentService.initiateDollarShieldTransaction(userDetails.getUsername(), realRateRequest));
    }

    @GetMapping("/get-investment/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> fetchAllRunningInvestmentsById(@PathVariable String id, @AuthenticationPrincipal UserDetails userDetails) {
        return toResponse(investmentService.fetchAllRunningInvestmentsById(userDetails.getUsername(), id));
    }

    @GetMapping("/get-investment-stats-products")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> fetchStatsInvestment(@AuthenticationPrincipal UserDetails userDetails) {
        return investmentService.getUserTotalInvestmentsStatsByAllProducts(userDetails.getUsername());
    }

    @GetMapping("/get-chart-data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getChartData(@AuthenticationPrincipal UserDetails userDetails) {
        return toResponse(investmentService.getChartData(userDetails.getUsername()));
    }

    @GetMapping("/initial-limit-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getInitialLimitStatus(@AuthenticationPrincipal UserDetails userDetails) {
        return toResponse(investmentService.getInitialLimitStatusForAllProducts(userDetails.getUsername()));
    }

    @GetMapping("/dollar-shield/approve/{investmentId}")
    public ResponseEntity<ApiResponse> approveInvestment(
            @PathVariable String investmentId,
            @RequestParam String token
    ) {
        return toResponse(investmentService.confirmInvestment(investmentId, token, true));
    }

    @GetMapping("/dollar-shield/decline/{investmentId}")
    public ResponseEntity<ApiResponse> declineInvestment(
            @PathVariable String investmentId,
            @RequestParam String token
    ) {
        return toResponse(investmentService.confirmInvestment(investmentId, token, false));
    }

    @GetMapping("/generate-reference")
    public ResponseEntity<ApiResponse> generateReference() {
        return toResponse(investmentService.generateRefCode());
    }

    @GetMapping("/product-total-investment/{product-id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getProductTotalInvestmentAmount(
            @PathVariable("product-id") String productId
    ) {
        return toResponse(investmentService.getProductTotalInvestmentAmountByProductId(productId));
    }


     @PostMapping("/product-investments/{product-id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getInvestmentsPerProduct(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("product-id") String productId,
            @RequestBody(required = false) InvestmentFilterRequest filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return toResponse(investmentService.fetchAllRunningInvestmentsByProduct(userDetails.getUsername(), productId, page, size));
    }

}
