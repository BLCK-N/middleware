package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.AssetAndInvestment;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FinancialWorldRequest(
        List<AssetAndInvestment> assetsAndInvestments,

        @Size(max = 255, message = "Other asset or investment must be at most 255 characters")
        String otherAssetOrInvestment
) {
}
