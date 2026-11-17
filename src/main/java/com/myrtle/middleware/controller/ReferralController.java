package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.referral.request.CustomInviteCodeRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.refer.ReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/refer")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    @PostMapping("/be-an-agent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> beAnAgent(@AuthenticationPrincipal UserDetails userDetails) {
        return referralService.beAnAgent(userDetails.getUsername());
    }

    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> dashboard(@AuthenticationPrincipal UserDetails userDetails) {
        return referralService.getDashboard(userDetails.getUsername());
    }

    @GetMapping("/invite-code/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> checkInviteCodeAvailability(@RequestParam("code") String code) {
        return referralService.checkInviteCodeAvailability(code);
    }

    @PatchMapping("/invite-code/customize")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> customizeInviteCode(@AuthenticationPrincipal UserDetails userDetails,
                                                           @RequestBody CustomInviteCodeRequest request) {
        return referralService.customizeInviteCode(userDetails.getUsername(), request.code());
    }

    @GetMapping("/retrieve-invite-code")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> retrieveInviteCode(@AuthenticationPrincipal UserDetails userDetails) {
        return referralService.getInviteCode(userDetails.getUsername());
    }

    @GetMapping("/code-lookup")
    public ResponseEntity<ApiResponse> lookupInviteCode(@RequestParam("code") String code) {
        return referralService.lookupInviteCode(code);
    }
    
    @GetMapping("/balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getReferralBalance(@AuthenticationPrincipal UserDetails userDetails) {
        return referralService.getReferralBalance(userDetails.getUsername());
    }

    @GetMapping("/balance-accruals")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getDailyBalanceAccruals(@AuthenticationPrincipal UserDetails userDetails) {
        return referralService.getBalanceAccruals(userDetails.getUsername());
    }

    @GetMapping("/my-referrals")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getAllReferrals(@AuthenticationPrincipal UserDetails userDetails) {
        return referralService.getMyReferrals(userDetails.getUsername());
    }
}
