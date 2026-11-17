package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.AssetAndInvestment;

import java.util.List;

public record FinancialWorldResponse(
        List<AssetAndInvestment> assetsAndInvestments,
        String otherAssetOrInvestment
) {
}
