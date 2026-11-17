package com.myrtle.middleware.dtos.referral.mywealth.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCreationDto {
    private String groupId;
    private String type;
    private String gender;
    private String label;
    private String title;
    private String firstName;
    private String lastName;
    private String maritalStatus;
    private String birthDate;
    private String email;
    private String mobileNo;
    private Address address;

    @Builder.Default
    private String status = "ACTIVE";

    @Builder.Default
    private boolean payoutEnabled = true;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Address {
        //        private String id;
        @Builder.Default
        private String type = "PRIMARY";
        private String addressLine1;
        private String city;
        private String state;
        private String postCode;
        private String country;
    }
}
