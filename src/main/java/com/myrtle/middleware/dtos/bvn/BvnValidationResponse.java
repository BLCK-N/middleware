package com.myrtle.middleware.dtos.bvn;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BvnValidationResponse {
    private String middleName;
    private boolean verified;
}
