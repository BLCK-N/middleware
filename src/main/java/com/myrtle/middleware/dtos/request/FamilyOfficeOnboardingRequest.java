package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.PriorityArea;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class FamilyOfficeOnboardingRequest {
    @NotNull(message = "Personal information is required")
    @Valid
    private PersonalInformationRequest personalInformation;

    @Valid
    private FamilyInformationRequest familyInformation;

    @Valid
    private WealthJourneyRequest wealthJourney;

    @Valid
    private FinancialWorldRequest financialWorld;

    private List<@Valid ImportantRelationshipRequest> importantRelationships;

    @Valid
    private FutureProtectionRequest futureProtection;

    @Valid
    private DocumentCheckRequest documentCheck;

    private List<PriorityArea> priorityAreas;

    @Size(max = 10000, message = "Final reflection must be at most 10000 characters")
    private String finalReflection;

    @Override
    public String toString() {
        return "{" +
                "finalReflection='" + finalReflection + '\'' +
                ", priorityAreas=" + priorityAreas +
                ", documentCheck=" + documentCheck +
                ", futureProtection=" + futureProtection +
                ", importantRelationships=" + importantRelationships +
                ", financialWorld=" + financialWorld +
                ", wealthJourney=" + wealthJourney +
                ", familyInformation=" + familyInformation +
                '}';
    }
}
