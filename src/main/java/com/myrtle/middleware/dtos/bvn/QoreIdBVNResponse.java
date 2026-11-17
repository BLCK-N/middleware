package com.myrtle.middleware.dtos.bvn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Collections;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class QoreIdBVNResponse {

    private int id;
    private Applicant applicant;
    private Summary summary;
    private Status status;
    private Bvn bvn;

    /* =======================
       MATCH HELPERS
       ======================= */

    public boolean isExactMatch() {
        return summary != null
                && summary.getBvnCheck() != null
                && summary.getBvnCheck().getStatus() == MatchStatus.EXACT_MATCH;
    }

    public boolean isPartialMatch() {
        return summary != null
                && summary.getBvnCheck() != null
                && summary.getBvnCheck().getStatus() == MatchStatus.PARTIAL_MATCH;
    }

    public boolean isTransposedMatch() {
        return summary != null
                && summary.getBvnCheck() != null
                && summary.getBvnCheck().getStatus() == MatchStatus.TRANSPOSED_MATCH;
    }

    public Map<String, Boolean> getFieldMatches() {
        if (summary == null || summary.getBvnCheck() == null) {
            return Collections.emptyMap();
        }
        return summary.getBvnCheck().getFieldMatches();
    }

    /* =======================
       INNER CLASSES
       ======================= */

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Applicant {
        private String firstname;
        private String lastname;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Summary {

        @JsonProperty("bvn_check")
        private BvnCheck bvnCheck;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class BvnCheck {

            private MatchStatus status;
            private Map<String, Boolean> fieldMatches;

            public long matchedFieldCount() {
                if (fieldMatches == null) return 0;
                return fieldMatches.values()
                        .stream()
                        .filter(Boolean::booleanValue)
                        .count();
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        private String state;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Bvn {

        private String bvn;
        private String firstname;
        private String lastname;
        private String middlename;
        private String birthdate;
        private String gender;
        private String phone;
        private String photo;

        @JsonProperty("lga_of_origin")
        private String lgaOfOrigin;

        @JsonProperty("marital_status")
        private String maritalStatus;

        private String nationality;

        @JsonProperty("residential_address")
        private String residentialAddress;

        @JsonProperty("state_of_origin")
        private String stateOfOrigin;

        @JsonProperty("state_of_residence")
        private String stateOfResidence;

        private String email;

        @JsonProperty("enrollment_bank")
        private String enrollmentBank;

        @JsonProperty("enrollment_branch")
        private String enrollmentBranch;

        private String title;

        @JsonProperty("name_on_card")
        private String nameOnCard;

        @JsonProperty("level_of_account")
        private String levelOfAccount;

        @JsonProperty("registration_date")
        private String registrationDate;

        @JsonProperty("watch_listed")
        private String watchListed;
    }

    /* =======================
       ENUMS
       ======================= */

    public enum MatchStatus {
        EXACT_MATCH,
        PARTIAL_MATCH,
        NO_MATCH,
        TRANSPOSED_MATCH,
        FAILED
    }
}
