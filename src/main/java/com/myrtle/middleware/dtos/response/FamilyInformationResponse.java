package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.FamilyMembersAbroadStatus;
import com.myrtle.middleware.enums.HouseholdMember;

import java.util.List;

public record FamilyInformationResponse(
        List<HouseholdMember> householdMembers,
        String otherHouseholdMember,
        Boolean hasFamilyMembersAbroad,
        FamilyMembersAbroadStatus familyMembersAbroadStatus,
        Boolean hasYoungerFamilyMembersToPrepare,
        Integer numberOfYoungerFamilyMembers,
        String youngerFamilyMembersAgeRange,
        List<String> familyValues
) {
}
