package com.myrtle.middleware.dtos.referral.response;

import java.time.LocalDateTime;

public record ReferralListItem(String name, LocalDateTime joined) {
}
