package com.myrtle.middleware.dtos.response;

import java.util.List;

public record LedgerReportResponse(
        String accountId,
        String accountLabel,
        String accountNo,
        String positionDate,
        String reportCurrency,
        double totalValue,
        double totalCost,
        List<PositionInstrument> positionInstruments,
        double gainLoss,
        double gainLossPercent
) {}
