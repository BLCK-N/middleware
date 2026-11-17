package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.JobListingStatus;
import com.myrtle.middleware.model.JobListing;

import java.time.LocalDateTime;
import java.util.List;

public record JobListingDetailResponse(
        String id,
        String title,
        String location,
        String description,
        List<String> requirements,
        String otherDetails,
        JobListingStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static JobListingDetailResponse from(JobListing jobListing) {
        return new JobListingDetailResponse(
                jobListing.getId(),
                jobListing.getTitle(),
                jobListing.getLocation(),
                jobListing.getDescription(),
                jobListing.getRequirements() == null ? List.of() : List.copyOf(jobListing.getRequirements()),
                jobListing.getOtherDetails(),
                jobListing.getStatus(),
                jobListing.getCreatedAt(),
                jobListing.getUpdatedAt()
        );
    }
}
