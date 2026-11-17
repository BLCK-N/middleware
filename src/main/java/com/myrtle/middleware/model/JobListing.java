package com.myrtle.middleware.model;

import com.myrtle.middleware.enums.JobListingStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only view of the job_listing table. The HR service owns this schema and is the only
 * writer, so this mapping follows that table rather than leading it.
 */
@Entity
@Immutable
@Table(
        name = "job_listing",
        indexes = {
                @Index(name = "idx_job_listing_status", columnList = "status"),
                @Index(name = "idx_job_listing_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class JobListing {

    @Id
    private String id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "location", nullable = false, length = 150)
    private String location;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "other_details", columnDefinition = "TEXT")
    private String otherDetails;

    /** One row per bullet in job_listing_requirement, ordered by its position column. */
    @ElementCollection
    @CollectionTable(
            name = "job_listing_requirement",
            joinColumns = @JoinColumn(name = "job_listing_id")
    )
    @OrderColumn(name = "position")
    @Column(name = "requirement", nullable = false, length = 500)
    private List<String> requirements = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobListingStatus status;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_by_email")
    private String createdByEmail;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
