package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessInformation {
    @NotBlank(message = "RC Number is required")
    @Size(max = 200, message = "RC Number must be at most 200 characters")
    private String rcNumber;
}
