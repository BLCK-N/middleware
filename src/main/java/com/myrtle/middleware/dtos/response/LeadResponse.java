package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeadResponse {
    private String id;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
    private boolean deleted;
    private String status;
    private String maritalStatus;
    private List<String> tags;
    private String gender;
    private String leadType;
    private String code;
    private String refCode;
    private String label;
    private String title;
    private String notes;
    private String firstName;
    private Boolean emailOptOut;
    private String middleName;
    private String lastName;
    private String leadSource;
    private String organization;
    private String email;
    private String mobileNo;
    private String officeNo;
    private Address address;
    private String birthDate; // If you prefer, use LocalDate
    private String clientId;
    private String pictureUrl;
    private String clientGroupId;
    private String clientGroupCode;
    private String clientGroupLabel;
    private String advisorGroupId;
    private String advisorGroupCode;
    private String advisorGroupLabel;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Address {
        private String id;
        private String type;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postCode;
        private String country;
    }
}
