package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.request.AddImage;
import com.myrtle.middleware.dtos.request.CreateAccountRequest;
import com.myrtle.middleware.dtos.request.EditUserRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.authentication.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Individual (non-corporate) user account creation and profile - the endpoints whose only concern
 * is the account itself, not login, PIN, tier upgrades, or other auth concerns which stay on
 * {@link AuthController}.
 */
@RestController
@RequestMapping(path = "api/v1/auth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/create-account")
    public ResponseEntity<ApiResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.doAccountCreation(request);
    }

    @PostMapping("/update-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> updateProfile(@RequestBody AddImage user, @AuthenticationPrincipal UserDetails userDetails) {
        return accountService.addProfileImage(userDetails.getUsername(), user.url());
    }

    @GetMapping("/user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return accountService.getUserProfile(userDetails.getUsername());
    }

    @PutMapping("/edit-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> editProfile(@AuthenticationPrincipal UserDetails userDetails, @RequestBody EditUserRequest editUserRequest) {
        return accountService.editUserDetails(editUserRequest, userDetails.getUsername());
    }
}
