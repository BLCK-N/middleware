package com.myrtle.middleware.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "family_office_onboarding")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FamilyOfficeOnboarding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "personal_information", columnDefinition = "TEXT")
    private String personalInformation;

    @Column(name = "family_information", columnDefinition = "TEXT")
    private String familyInformation;

    @Column(name = "wealth_journey", columnDefinition = "TEXT")
    private String wealthJourney;

    @Column(name = "financial_world", columnDefinition = "TEXT")
    private String financialWorld;

    @Column(name = "important_relationships", columnDefinition = "TEXT")
    private String importantRelationships;

    @Column(name = "future_protection", columnDefinition = "TEXT")
    private String futureProtection;

    @Column(name = "document_check", columnDefinition = "TEXT")
    private String documentCheck;

    @Column(name = "priority_areas", columnDefinition = "TEXT")
    private String priorityAreas;

    @Column(name = "final_reflection", columnDefinition = "TEXT")
    private String finalReflection;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "search_name")
    private String searchName;

    @Column(name = "search_email")
    private String searchEmail;

    @Column(name = "search_country")
    private String searchCountry;

    @Column(name = "search_city")
    private String searchCity;

    @Column(name = "search_income_range")
    private String searchIncomeRange;

    @Column(name = "search_marital_status")
    private String searchMaritalStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void normalizeSearchFields() {
        this.searchName = normalize(searchName);
        this.searchEmail = normalize(searchEmail);
        this.searchCountry = normalize(searchCountry);
        this.searchCity = normalize(searchCity);
        this.searchIncomeRange = normalize(searchIncomeRange);
        this.searchMaritalStatus = normalize(searchMaritalStatus);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }
}
