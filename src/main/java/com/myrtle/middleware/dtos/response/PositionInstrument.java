package com.myrtle.middleware.dtos.response;

public record PositionInstrument(
        String instrumentId,
        String instrumentType,
        String secId,
        String secDesc,
        String secNotes,
        String isin,
        String marketCode,
        String instrumentCurrency,
        double currencyRate,
        double currentPrice,
        double priceConvRatio,
        double parValue,
        double quantity,
        double avgCost,
        double totalCost,
        double currentValue,
        String positionStartDate,
        double interest,
        String tradingAccountNo
) {}
