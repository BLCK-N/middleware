package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.request.*;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.corporate.CorporateAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping(path = "api/v1/auth/corporate")
@RequiredArgsConstructor
public class CorporateAccountController {

    private final CorporateAccountService corporateAccountService;

    @GetMapping("/start/{email}")
    public ResponseEntity<?> validateEmail(@PathVariable String email) {
        return corporateAccountService.sendSimpleOtp(email);
    }

    @PostMapping("/validate-otp")
    public ResponseEntity<ApiResponse> validateOtp(@Valid @RequestBody ValidteOtpForCorporate request) {
        return corporateAccountService.validateOtpAndGetToken(request);
    }

    // not tied to a specific page.
    @GetMapping("/preview")
    public ResponseEntity<ApiResponse> previewPendingCorporateData() {
        return corporateAccountService.getPendingCorporateData();
    }

    @GetMapping("/submission")
    public ResponseEntity<ApiResponse> getCorporateSubmission() {
        return corporateAccountService.getFullSubmission();
    }


    // page 1
    @GetMapping("/basic-details")
    public ResponseEntity<ApiResponse> getCorporateBasicDetails() {
        return corporateAccountService.getBasicDetails();
    }

    @PostMapping("/basic-details")
    public ResponseEntity<ApiResponse> collectCorporateBasicDetails(@Valid @RequestBody BasicDetails basicDetails) {
        return corporateAccountService.collectBasicDetails(basicDetails);
    }

    // page 2 - the read side lives at /bvn-data (the /validate-bvn POST verifies and stores).
    @GetMapping("/bvn-data")
    public ResponseEntity<ApiResponse> getBvnDetails() {
        return corporateAccountService.getBvnDetails();
    }

    @PostMapping("/validate-bvn")
    public ResponseEntity<ApiResponse> validateBvn(@Valid @RequestBody ValidateBvnRequest request) {
        return corporateAccountService.insureBvnIsCorrect(request);
    }

    @PatchMapping("/validate-bvn")
    public ResponseEntity<ApiResponse> updateBvn(@RequestBody ValidateBvnRequest request) {
        return corporateAccountService.updateBvn(request);
    }

    // page 4
    @GetMapping("/kyb")
    public ResponseEntity<ApiResponse> getCorporateKyb() {
        return corporateAccountService.getKybDetails();
    }

    @PostMapping("/kyb")
    public ResponseEntity<ApiResponse> collectCorporateKyb(@Valid @RequestBody BusinessInformation businessInformation) {
        return corporateAccountService.verifyAndCollectKYB(businessInformation);
    }

    @PatchMapping("/kyb")
    public ResponseEntity<ApiResponse> updateCorporateKyb(@RequestBody BusinessInformation businessInformation) {
        return corporateAccountService.updateBusinessInformation(businessInformation);
    }

    // page 5
    @GetMapping("/memart")
    public ResponseEntity<ApiResponse> getCorporateMemart() {
        return corporateAccountService.getMemartFile();
    }

    @PostMapping("/memart")
    public ResponseEntity<ApiResponse> collectCorporateMemart(@Valid @RequestBody MemartFileRequest memartFileRequest) {
        return corporateAccountService.collectMemartFile(memartFileRequest);
    }

    @PatchMapping("/memart")
    public ResponseEntity<ApiResponse> updateCorporateMemart(@RequestBody MemartFileRequest memartFileRequest) {
        return corporateAccountService.updateMemart(memartFileRequest);
    }

    // page 6
    @GetMapping("/directors")
    public ResponseEntity<ApiResponse> getCorporateDirectors() {
        return corporateAccountService.getDirectors();
    }

    @PostMapping("/directors")
    public ResponseEntity<ApiResponse> collectCorporateDirectors(
            @Valid @RequestBody List<DirectorPayload> directors
    ) {
        return corporateAccountService.collectDirectors(directors);
    }

    @PatchMapping("/directors")
    public ResponseEntity<ApiResponse> updateCorporateDirectors(
            @RequestBody List<DirectorPayload> directors
    ) {
        return corporateAccountService.updateDirectors(directors);
    }

    // page 7
    @GetMapping("/credentials")
    public ResponseEntity<ApiResponse> getCorporateCredentials() {
        return corporateAccountService.getCorporateCredentials();
    }

    @PostMapping("/credentials")
    public ResponseEntity<ApiResponse> collectCorporateCredentials(
            @Valid @RequestBody CorporateCredentialsRequest request
    ) {
        return corporateAccountService.collectCorporateCredentials(request);
    }

    @PatchMapping("/credentials")
    public ResponseEntity<ApiResponse> updateCorporateCredentials(
            @RequestBody CorporateCredentialsRequest request
    ) {
        return corporateAccountService.updateCorporateCredentials(request);
    }

    // page 8
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse> completeCorporateOnboarding() {
        return corporateAccountService.completeCorporateOnboarding();
    }

    // after account has been successfully created
    @PostMapping("/lookup")
    public ResponseEntity<ApiResponse> lookupCorporateAccount(@Valid @RequestBody CorporateLookupRequest request) {
        return corporateAccountService.lookupCorporateAccount(request);
    }

    @PostMapping("/create-user")
    public ResponseEntity<ApiResponse> createCorporateUser(@Valid @RequestBody CreateCorporateUserRequest request) {
        return corporateAccountService.createCorporateUserMain(request);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> loginCorporateAccount(@RequestBody UserLogin user) {
        return corporateAccountService.loginCorporateAccount(user);
    }
}
