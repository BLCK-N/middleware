package com.myrtle.middleware.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApproveHolderDetailsRequest {
    private String userId;
    private String notes;
}
