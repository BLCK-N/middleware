package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.Currency;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RealRateRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private Currency currency;
}
