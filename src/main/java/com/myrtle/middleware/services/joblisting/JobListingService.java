package com.myrtle.middleware.services.joblisting;

import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.JobListingDetailResponse;
import com.myrtle.middleware.dtos.response.JobListingSummaryResponse;
import com.myrtle.middleware.model.JobListing;
import com.myrtle.middleware.repository.JobListingRepository;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobListingService {

    private static final String FETCH_ALL_MESSAGE = "Job listings retrieved successfully";
    private static final String FETCH_SINGLE_MESSAGE = "Job listing retrieved successfully";
    private static final String NOT_FOUND_MESSAGE = "Job listing not found for id: ";

    private final JobListingRepository jobListingRepository;
    private final ResponseUtil responseUtil;

    public ResponseEntity<ApiResponse> findOpenListings(int page, int size, String location, String search) {
        String locationFilter = normalizeFilter(location);
        String searchFilter = normalizeFilter(search);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<JobListing> listings = jobListingRepository.findOpenListings(locationFilter, searchFilter, pageable);

        log.debug("Public job listings retrieved | page={} | size={} | location={} | search={} | totalItems={}",
                page, size, locationFilter, searchFilter, listings.getTotalElements());

        return responseUtil.success(FETCH_ALL_MESSAGE, pageResponse(listings));
    }

    public ResponseEntity<ApiResponse> findById(String id) {
        return jobListingRepository.findWithRequirementsByIdAndDeletedFalse(id)
                .map(listing -> responseUtil.success(FETCH_SINGLE_MESSAGE, JobListingDetailResponse.from(listing)))
                .orElseGet(() -> {
                    log.warn("Job listing not found | id={}", id);
                    return responseUtil.notFound(NOT_FOUND_MESSAGE + id, null);
                });
    }

    private Map<String, Object> pageResponse(Page<JobListing> page) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", page.getContent().stream().map(JobListingSummaryResponse::from).toList());
        data.put("currentPage", page.getNumber());
        data.put("totalPages", page.getTotalPages());
        data.put("totalItems", page.getTotalElements());
        data.put("pageSize", page.getSize());
        return data;
    }

    /** Blank/absent filters become {@code ""} so the JPQL bind always has a typed string for Postgres. */
    private String normalizeFilter(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
