package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.JobListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JobListingRepository extends JpaRepository<JobListing, String> {

    /**
     * Soft-deleted and filled listings are excluded here so the public feed cannot leak them.
     *
     * <p>Empty-string sentinels are used instead of {@code null} for optional filters: PostgreSQL
     * cannot infer a type for unbound null bind parameters in {@code :param is null OR ...} JPQL,
     * which makes the unfiltered public feed fail while filtered requests succeed.
     */
    @Query("""
            select j from JobListing j
            where j.deleted = false
              and j.status = com.myrtle.middleware.enums.JobListingStatus.OPEN
              and (:location = '' or lower(j.location) like lower(concat('%', :location, '%')))
              and (:search = '' or lower(j.title) like lower(concat('%', :search, '%')))
            """)
    Page<JobListing> findOpenListings(
            @Param("location") String location,
            @Param("search") String search,
            Pageable pageable
    );

    /**
     * Requirements are fetched in this one query because nothing here runs inside a transaction,
     * so the collection cannot be loaded lazily once the repository call returns.
     *
     * <p>Status is deliberately not filtered: a filled role still resolves, so a link that was
     * shared while the role was open shows it as filled instead of 404-ing.
     */
    @EntityGraph(attributePaths = "requirements")
    Optional<JobListing> findWithRequirementsByIdAndDeletedFalse(String id);
}
