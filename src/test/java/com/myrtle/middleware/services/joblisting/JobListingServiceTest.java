package com.myrtle.middleware.services.joblisting;

import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.JobListingDetailResponse;
import com.myrtle.middleware.dtos.response.JobListingSummaryResponse;
import com.myrtle.middleware.enums.JobListingStatus;
import com.myrtle.middleware.model.JobListing;
import com.myrtle.middleware.repository.JobListingRepository;
import com.myrtle.middleware.utils.ResponseUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobListingServiceTest {

    @Mock private JobListingRepository jobListingRepository;

    private JobListingService service;

    @BeforeEach
    void setUp() {
        service = new JobListingService(jobListingRepository, new ResponseUtil());
    }

    @Test
    void returnsOpenListingsNewestFirst() {
        JobListing listing = jobListing("job-1", "Senior Backend Engineer", "Lagos, Nigeria");
        when(jobListingRepository.findOpenListings(eq(""), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing), PageRequest.of(0, 20), 1));

        ResponseEntity<ApiResponse> response = service.findOpenListings(0, 20, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Job listings retrieved successfully");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertThat(data.get("currentPage")).isEqualTo(0);
        assertThat(data.get("totalPages")).isEqualTo(1);
        assertThat(data.get("totalItems")).isEqualTo(1L);
        assertThat(data.get("pageSize")).isEqualTo(20);

        @SuppressWarnings("unchecked")
        List<JobListingSummaryResponse> content = (List<JobListingSummaryResponse>) data.get("content");
        assertThat(content).containsExactly(
                new JobListingSummaryResponse("job-1", "Senior Backend Engineer", "Lagos, Nigeria", JobListingStatus.OPEN)
        );
    }

    @Test
    void sortsByCreatedAtDescending() {
        when(jobListingRepository.findOpenListings(any(), any(), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.findOpenListings(2, 50, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(jobListingRepository).findOpenListings(any(), any(), pageable.capture());

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("createdAt").descending());
    }

    @Test
    void trimsFiltersAndTreatsBlankOnesAsAbsent() {
        when(jobListingRepository.findOpenListings(eq("Lagos"), eq(""), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.findOpenListings(0, 20, "  Lagos  ", "   ");

        verify(jobListingRepository).findOpenListings(eq("Lagos"), eq(""), any(Pageable.class));
    }

    @Test
    void returnsListingDetailsWithItsRequirements() {
        JobListing listing = jobListing("job-1", "Senior Backend Engineer", "Lagos, Nigeria");
        listing.setOtherDetails("Hybrid role. Monday to Friday.");
        listing.setRequirements(List.of("5+ years of Java experience", "Strong Spring Boot experience"));
        when(jobListingRepository.findWithRequirementsByIdAndDeletedFalse("job-1"))
                .thenReturn(Optional.of(listing));

        ResponseEntity<ApiResponse> response = service.findById("job-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Job listing retrieved successfully");

        JobListingDetailResponse data = (JobListingDetailResponse) response.getBody().getData();
        assertThat(data.id()).isEqualTo("job-1");
        assertThat(data.title()).isEqualTo("Senior Backend Engineer");
        assertThat(data.otherDetails()).isEqualTo("Hybrid role. Monday to Friday.");
        assertThat(data.status()).isEqualTo(JobListingStatus.OPEN);
        assertThat(data.requirements())
                .containsExactly("5+ years of Java experience", "Strong Spring Boot experience");
    }

    @Test
    void stillReturnsAFilledListingSoASharedLinkDoesNot404() {
        JobListing listing = jobListing("job-1", "Product Designer", "Abuja, Nigeria");
        listing.setStatus(JobListingStatus.FILLED);
        when(jobListingRepository.findWithRequirementsByIdAndDeletedFalse("job-1"))
                .thenReturn(Optional.of(listing));

        ResponseEntity<ApiResponse> response = service.findById("job-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JobListingDetailResponse data = (JobListingDetailResponse) response.getBody().getData();
        assertThat(data.status()).isEqualTo(JobListingStatus.FILLED);
    }

    @Test
    void returnsNotFoundForAnUnknownOrDeletedListing() {
        when(jobListingRepository.findWithRequirementsByIdAndDeletedFalse("missing"))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse> response = service.findById("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Job listing not found for id: missing");
        assertThat(response.getBody().getData()).isNull();
    }

    private Page<JobListing> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
    }

    private JobListing jobListing(String id, String title, String location) {
        JobListing listing = new JobListing();
        listing.setId(id);
        listing.setTitle(title);
        listing.setLocation(location);
        listing.setDescription("We are looking for a " + title + ".");
        listing.setStatus(JobListingStatus.OPEN);
        listing.setCreatedAt(LocalDateTime.now());
        return listing;
    }
}
