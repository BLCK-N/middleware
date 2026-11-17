package com.myrtle.middleware.repository;

import com.myrtle.middleware.enums.JobListingStatus;
import com.myrtle.middleware.model.JobListing;
import com.myrtle.middleware.utils.FieldEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(FieldEncryptor.class) // required by the encrypted-column converter on the corporate entities
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-listings;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.encryption.key=job-listing-repository-test-key"
})
class JobListingRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private JobListingRepository jobListingRepository;

    @Test
    void excludesFilledAndDeletedListings() {
        persist("Senior Backend Engineer", "Lagos, Nigeria", JobListingStatus.OPEN, false);
        persist("Product Designer", "Abuja, Nigeria", JobListingStatus.FILLED, false);
        persist("Data Analyst", "Lagos, Nigeria", JobListingStatus.OPEN, true);

        Page<JobListing> listings = jobListingRepository.findOpenListings("", "", page());

        assertThat(listings.getContent())
                .extracting(JobListing::getTitle)
                .containsExactly("Senior Backend Engineer");
    }

    @Test
    void matchesLocationAndTitlePartiallyAndIgnoringCase() {
        persist("Senior Backend Engineer", "Lagos, Nigeria", JobListingStatus.OPEN, false);
        persist("Frontend Engineer", "Abuja, Nigeria", JobListingStatus.OPEN, false);

        assertThat(jobListingRepository.findOpenListings("lagos", "", page()).getContent())
                .extracting(JobListing::getTitle)
                .containsExactly("Senior Backend Engineer");

        assertThat(jobListingRepository.findOpenListings("", "BACKEND", page()).getContent())
                .extracting(JobListing::getTitle)
                .containsExactly("Senior Backend Engineer");

        assertThat(jobListingRepository.findOpenListings("Lagos", "Frontend", page()).getContent())
                .isEmpty();
    }

    @Test
    void returnsNewestFirst() {
        JobListing older = persist("Older Role", "Lagos, Nigeria", JobListingStatus.OPEN, false);
        older.setCreatedAt(LocalDateTime.now().minusDays(3));
        entityManager.persist(older);

        persist("Newer Role", "Lagos, Nigeria", JobListingStatus.OPEN, false);
        entityManager.flush();

        assertThat(jobListingRepository.findOpenListings("", "", page()).getContent())
                .extracting(JobListing::getTitle)
                .containsExactly("Newer Role", "Older Role");
    }

    @Test
    void loadsRequirementsInPositionOrderOutsideATransaction() {
        JobListing listing = persist("Senior Backend Engineer", "Lagos, Nigeria", JobListingStatus.OPEN, false);
        listing.setRequirements(List.of("5+ years of Java experience", "Strong Spring Boot experience"));
        entityManager.persistAndFlush(listing);
        entityManager.clear();

        JobListing found = jobListingRepository.findWithRequirementsByIdAndDeletedFalse(listing.getId()).orElseThrow();

        assertThat(found.getRequirements())
                .containsExactly("5+ years of Java experience", "Strong Spring Boot experience");
    }

    @Test
    void resolvesFilledListingsButNotDeletedOnes() {
        JobListing filled = persist("Product Designer", "Abuja, Nigeria", JobListingStatus.FILLED, false);
        JobListing deleted = persist("Data Analyst", "Lagos, Nigeria", JobListingStatus.OPEN, true);

        assertThat(jobListingRepository.findWithRequirementsByIdAndDeletedFalse(filled.getId()))
                .isPresent();
        assertThat(jobListingRepository.findWithRequirementsByIdAndDeletedFalse(deleted.getId()))
                .isEmpty();
    }

    private PageRequest page() {
        return PageRequest.of(0, 20, Sort.by("createdAt").descending());
    }

    private JobListing persist(String title, String location, JobListingStatus status, boolean deleted) {
        JobListing listing = new JobListing();
        listing.setId(UUID.randomUUID().toString());
        listing.setTitle(title);
        listing.setLocation(location);
        listing.setDescription("We are looking for a " + title + ".");
        listing.setStatus(status);
        listing.setDeleted(deleted);
        listing.setCreatedAt(LocalDateTime.now());
        entityManager.persist(listing);
        entityManager.flush();
        return listing;
    }
}
