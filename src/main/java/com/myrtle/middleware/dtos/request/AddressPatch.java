package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressPatch {

    private String id;
    private String type; // e.g. PRIMARY

    private String addressLine1;
    private String addressLine2;

    private String city;
    private String state;
    private String postCode;
    private String country;
}
