package com.myrtle.middleware.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.myrtle.middleware.dtos.bvn.AddBVN;
import com.myrtle.middleware.dtos.bvn.BVNRequest;
import com.myrtle.middleware.dtos.request.*;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.enums.Status;
import com.myrtle.middleware.repository.NinVerificationRequest;
import com.myrtle.middleware.services.authentication.AuthService;
import com.myrtle.middleware.services.authentication.BiometricAuthService;
import com.myrtle.middleware.utils.JwtUtils;
import com.myrtle.middleware.utils.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RestController
@RequestMapping(path = "api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final BiometricAuthService biometricAuthService;
    private final ResponseUtil responseUtil;
    private final JwtUtils jwtUtil;
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse> sendOtp(@RequestBody SendOtp sendOtp) {
        return authService.sendOtp(sendOtp);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse> verifyOtp(@RequestBody VerifyDto verifyDto) {
        return authService.verifyOtp(verifyDto);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(
            @RequestBody UserLogin user,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Type", required = false) String deviceType
    ) throws JsonProcessingException {
        return authService.login(user, deviceId, deviceType);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return authService.refreshSession(request);
    }

    @PostMapping("/device-details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> addDeviceDetails(
            @Valid @RequestBody DeviceDetailsRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return authService.saveDeviceDetails(userDetails.getUsername(), request);
    }

    @PostMapping("/biometric/enable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> enableBiometric(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Type", required = false) String deviceType
    ) {
        return biometricAuthService.enableForUser(userDetails.getUsername(), deviceId, deviceType);
    }

    @PostMapping("/biometric/revoke")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> revokeBiometric(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Type", required = false) String deviceType
    ) {
        return biometricAuthService.revokeForUser(userDetails.getUsername(), deviceId, deviceType);
    }

    @PostMapping("/add-bvn")
    public ResponseEntity<ApiResponse> addBvn(@RequestBody AddBVN request, @AuthenticationPrincipal UserDetails userDetails) {
        return authService.collectAndUpdateBVN(userDetails.getUsername(), request.bvn());
    }

    @PostMapping("/add-pin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> addPin(@RequestBody PinRequest pinRequest, @AuthenticationPrincipal UserDetails userDetails) {
        return authService.addPin(userDetails.getUsername(), pinRequest.pin(), pinRequest.usePin());
    }

    @PostMapping("/change-pin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> changePin(@RequestBody ChangePinRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        return authService.changePIN(userDetails.getUsername(), request);
    }

    @PostMapping("/pin-login")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> loginWithPIN(@RequestBody PinLoginRequest pinRequest, @AuthenticationPrincipal UserDetails userDetails) {
        return authService.loginWithPin(userDetails.getUsername(), pinRequest.pin());
    }

    @PostMapping("/add-fcm-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> addFcmToken(@RequestBody FCMTokenRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        return authService.saveFcmToken(userDetails.getUsername(), request.token());
    }

    @PostMapping("/verify-bvn")
    public ResponseEntity<ApiResponse> verifyBvn(@Valid @RequestBody BVNRequest request) {
        return authService.verifyBVN(request);
    }

    @PostMapping("/upgrade-tier-2")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> upgradeToTire2(@RequestBody NinVerificationRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        return authService.verifyNIN(request, userDetails.getUsername());
    }

    @PostMapping("/upgrade-tier-3")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> upgradeToTire3(@RequestBody Tier3UpgradePayLoad request, @AuthenticationPrincipal UserDetails userDetails) {
        return authService.upgradeToTier3(request, userDetails.getUsername());
    }

    @PostMapping("/upgrade-tier-1-to-3")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> migrateTierOneToThree(@RequestBody TierMigrationPayload request, @AuthenticationPrincipal UserDetails userDetails) {
        return authService.migrateTierOneToThree(request, userDetails.getUsername());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@RequestBody ResetRequest request) {
        return authService.resetPassword(request);
    }

    @PutMapping("/pep")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> updatePep(@AuthenticationPrincipal UserDetails userDetails, @RequestBody UpdatePepRequest request) {
        return authService.updatePepDetails(userDetails.getUsername(), request);
    }

    @PutMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> changePassword(@AuthenticationPrincipal UserDetails userDetails, @RequestBody ChangePassword changePassword) {
        return authService.changePassword(userDetails.getUsername(), changePassword);
    }

    @PutMapping("/disable-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> disableAccount(@AuthenticationPrincipal UserDetails userDetails) {
        return authService.disableAccount(userDetails.getUsername());
    }

    @PutMapping("/delete-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> deleteAccount(@AuthenticationPrincipal UserDetails userDetails) {
        return authService.deleteAccount(userDetails.getUsername());
    }

    @GetMapping("/approve")
    public ResponseEntity<ApiResponse> approveEdit(@RequestParam String token) {
        if (token == null || token.isEmpty()) {
            return responseUtil.badRequest("Token is required", null);
        }

        String email;
        try {
            email = jwtUtil.extractUsername(token);
        } catch (Exception e) {
            return responseUtil.unauthorized("Invalid or expired token", null);
        }

        return authService.updateStatus(email, Status.VERIFIED);
    }

    @GetMapping("/decline")
    public ResponseEntity<ApiResponse> declineEdit(@RequestParam String token) {
        if (token == null || token.isEmpty()) {
            return responseUtil.badRequest("Token is required", null);
        }

        String email;
        try {
            email = jwtUtil.extractUsername(token);
        } catch (Exception e) {
            return responseUtil.unauthorized("Invalid or expired token", null);
        }

        return authService.updateStatus(email, Status.NOT_VERIFIED);
    }

    @GetMapping("/tier3/approve")
    public ResponseEntity<ApiResponse> approveTier3(@RequestParam String token) {
        if (token == null || token.isEmpty()) {
            return responseUtil.badRequest("Token is required", null);
        }

        String email;
        try {
            email = jwtUtil.extractUsername(token);
        } catch (Exception e) {
            return responseUtil.unauthorized("Invalid or expired token", null);
        }

        return authService.updateAddHolderStatus(email, Status.VERIFIED);
    }

    @GetMapping("/tier3/decline")
    public ResponseEntity<ApiResponse> declineTier3(@RequestParam String token) {
        if (token == null || token.isEmpty()) {
            return responseUtil.badRequest("Token is required", null);
        }

        String email;
        try {
            email = jwtUtil.extractUsername(token);
        } catch (Exception e) {
            return responseUtil.unauthorized("Invalid or expired token", null);
        }

        return authService.updateAddHolderStatus(email, Status.NOT_VERIFIED);
    }
}
