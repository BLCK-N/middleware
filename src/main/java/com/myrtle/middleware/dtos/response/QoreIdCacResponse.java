package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QoreIdCacResponse {
    private Long id;
    private Applicant applicant;
    private Summary summary;
    private Status status;
    private Map<String, Object> cac;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Applicant {
        private String firstname;
        private String lastname;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Summary {
        private Map<String, Object> cacCheck;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        private String state;
        private String status;
    }
}
