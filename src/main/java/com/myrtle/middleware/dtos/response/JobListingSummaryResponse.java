package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.JobListingStatus;
import com.myrtle.middleware.model.JobListing;

public record JobListingSummaryResponse(
        String id,
        String title,
        String location,
        JobListingStatus status
) {
    public static JobListingSummaryResponse from(JobListing jobListing) {
        return new JobListingSummaryResponse(
                jobListing.getId(),
                jobListing.getTitle(),
                jobListing.getLocation(),
                jobListing.getStatus()
        );
    }
}
