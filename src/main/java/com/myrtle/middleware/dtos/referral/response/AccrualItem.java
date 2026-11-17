package com.myrtle.middleware.dtos.referral.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccrualItem(String currency, BigDecimal amount, LocalDate date) {
}
