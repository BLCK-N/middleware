package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.FamilyKnowsWhereEverythingIs;
import com.myrtle.middleware.enums.FinancialAchievement;
import com.myrtle.middleware.enums.FinancialConcern;

import java.util.List;

public record WealthJourneyResponse(
        List<FinancialAchievement> financialAchievements,
        String otherFinancialAchievement,
        String currentlyBuilding,
        List<FinancialConcern> financialConcerns,
        String otherFinancialConcern,
        FamilyKnowsWhereEverythingIs familyKnowsWhereEverythingIs
) {
}
