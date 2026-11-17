package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Looks up an existing corporate account so an additional login can be attached to it.
 * The RC number is public CAC data - the registered BVN is what actually gates the lookup.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CorporateLookupRequest(

        @NotBlank(message = "RC number is required")
        String rcNumber,

        @NotBlank(message = "BVN is required")
        @Pattern(regexp = "^[0-9]{11}$", message = "BVN must be exactly 11 digits")
        String bvn
) {
}
