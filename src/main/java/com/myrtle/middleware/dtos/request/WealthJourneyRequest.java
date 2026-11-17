package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.FamilyKnowsWhereEverythingIs;
import com.myrtle.middleware.enums.FinancialAchievement;
import com.myrtle.middleware.enums.FinancialConcern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WealthJourneyRequest(
        List<FinancialAchievement> financialAchievements,

        @Size(max = 255, message = "Other financial achievement must be at most 255 characters")
        String otherFinancialAchievement,

        @Size(max = 1000, message = "Currently building must be at most 1000 characters")
        String currentlyBuilding,

        List<FinancialConcern> financialConcerns,

        @Size(max = 255, message = "Other financial concern must be at most 255 characters")
        String otherFinancialConcern,

        FamilyKnowsWhereEverythingIs familyKnowsWhereEverythingIs
) {
}
