package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.request.FamilyOfficeOnboardingRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.enums.MaritalStatus;
import com.myrtle.middleware.services.familyoffice.FamilyOfficeOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/family-office-onboarding")
@RequiredArgsConstructor
public class FamilyOfficeOnboardingController {

    private final FamilyOfficeOnboardingService familyOfficeOnboardingService;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody FamilyOfficeOnboardingRequest request) {
        return familyOfficeOnboardingService.create(request);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> findAll(Pageable pageable) {
        return familyOfficeOnboardingService.findAll(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> findById(@PathVariable String id) {
        return familyOfficeOnboardingService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> update(
            @PathVariable String id,
            @Valid @RequestBody FamilyOfficeOnboardingRequest request
    ) {
        return familyOfficeOnboardingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> delete(@PathVariable String id) {
        return familyOfficeOnboardingService.delete(id);
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String incomeRange,
            @RequestParam(required = false) MaritalStatus maritalStatus,
            Pageable pageable
    ) {
        return familyOfficeOnboardingService.search(
                name,
                email,
                country,
                city,
                incomeRange,
                maritalStatus,
                pageable
        );
    }
}
