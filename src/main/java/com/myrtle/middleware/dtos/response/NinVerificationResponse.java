package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class NinVerificationResponse {
    private Long id;
    private Applicant applicant;
    private Summary summary;

    @JsonProperty("status")
    private Verification verification;

    @JsonProperty("nin")
    private NinRecord ninRecord;

    // Applicant object
    @Data
    public static class Applicant {
        private String firstname;
        private String lastname;
        private String paymentMethodCode;
    }

    // Summary object (contains nested nin_check)
    @Data
    public static class Summary {
        @JsonProperty("nin_check")
        private NinCheck ninCheck;

        @Data
        public static class NinCheck {
            private String status;
            private Map<String, Boolean> fieldMatches;
        }
    }

    // Verification object
    @Data
    public static class Verification {
        private String state;
        private String status;
    }

    // NinRecord object (may be null in mismatch cases)
    @Data
    public static class NinRecord {
        private String nin;
        private String firstname;
        private String lastname;
        private String middlename;
        private String phone;
        private String gender;
        private String photo; // base64 string
    }
}
