package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.FamilyMembersAbroadStatus;
import com.myrtle.middleware.enums.HouseholdMember;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FamilyInformationRequest(
        List<HouseholdMember> householdMembers,

        @Size(max = 150, message = "Other household member must be at most 150 characters")
        String otherHouseholdMember,

        Boolean hasFamilyMembersAbroad,
        FamilyMembersAbroadStatus familyMembersAbroadStatus,
        Boolean hasYoungerFamilyMembersToPrepare,

        @Min(value = 0, message = "Number of younger family members cannot be negative")
        Integer numberOfYoungerFamilyMembers,

        @Size(max = 100, message = "Younger family members age range must be at most 100 characters")
        String youngerFamilyMembersAgeRange,

        List<@Size(max = 100, message = "Family value must be at most 100 characters") String> familyValues
) {
}
