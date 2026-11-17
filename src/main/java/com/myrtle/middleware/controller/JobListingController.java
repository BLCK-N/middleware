package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.joblisting.JobListingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-listings")
@RequiredArgsConstructor
@Validated
public class JobListingController {

    private final JobListingService jobListingService;

    @GetMapping
    public ResponseEntity<ApiResponse> getOpenJobListings(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must not be negative") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") int size,

            @RequestParam(required = false)
            @Size(max = 100, message = "location must not exceed 100 characters") String location,

            @RequestParam(required = false)
            @Size(max = 100, message = "search must not exceed 100 characters") String search
    ) {
        return jobListingService.findOpenListings(page, size, location, search);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getJobListing(@PathVariable String id) {
        return jobListingService.findById(id);
    }
}
