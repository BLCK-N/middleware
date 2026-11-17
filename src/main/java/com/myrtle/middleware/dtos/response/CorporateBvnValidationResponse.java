package com.myrtle.middleware.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CorporateBvnValidationResponse {
    private String middleName;
    private boolean verified;
}
