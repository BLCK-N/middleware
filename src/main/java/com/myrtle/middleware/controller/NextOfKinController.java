package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.request.NextOfKinRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.nextofkin.NextOfKinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/kin")
@RequiredArgsConstructor
public class NextOfKinController {

    private final NextOfKinService nextOfKinService;

    @PostMapping("/next-of-kin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> addNextOfKin(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody NextOfKinRequest request
    ) {
        return nextOfKinService.addNextOfKin(userDetails.getUsername(), request);
    }

    @GetMapping("/next-of-kin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getNextOfKins(@AuthenticationPrincipal UserDetails userDetails) {
        return nextOfKinService.getNextOfKins(userDetails.getUsername());
    }

    @PutMapping("/next-of-kin/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> updateNextOfKin(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody NextOfKinRequest request
    ) {
        return nextOfKinService.updateNextOfKin(id, userDetails.getUsername(), request);
    }

    @DeleteMapping("/next-of-kin/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> deleteNextOfKin(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return nextOfKinService.deleteNextOfKin(id, userDetails.getUsername());
    }
}
