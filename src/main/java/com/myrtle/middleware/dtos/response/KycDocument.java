package com.myrtle.middleware.dtos.response;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class KycDocument {

    private String id;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private Boolean deleted;

    private RecordType recordType;
    private IdType idType;

    private String idNo;
    private String idIssuer;
    private LocalDate idExpDate;

    private Status status;

    private String fileType;
    private String fileName;
    private Integer fileSize;

    private String notes;
    private String workflowNotes;

    private LocalDate validUntil;

    private KycReport kycReport;

    private String fileUrl;
    private String refCode;
    private String contactId;
    private String clientId;

    // ------------------------ ENUMS ------------------------

    public enum RecordType {
        PICTURE,
        SIGNATURE,
        IDENTITY,
        ADDRESS,
        OTHERS
    }

    public enum IdType {
        PASSPORT,
        NATIONAL_ID,
        DRIVERS_LICENSE,
        COMPANY_ID,
        VOTER_ID,
        BIRTH_CERTIFICATE,
        RESIDENCE_PERMIT,
        WORK_PERMIT,
        STUDENT_ID,
        SOCIAL_SECURITY_CARD,
        CERTIFICATE_OF_CITIZENSHIP,
        CERTIFICATE_OF_INCORPORATION,
        CERTIFICATE_OF_LIQUIDATION,
        CERTIFICATE_OF_NATURALIZATION,
        CERTIFICATE_OF_RESIDENCE,
        UTILITY_BILL,
        BANK_STATEMENT,
        PAYSLIP,
        TAX_RETURN,
        OTHER_DOCUMENTS,
        REFERENCE_LETTER
    }

    public enum Status {
        SUBMITTED,
        ACCEPTED,
        PROCESSED,
        DELIVERED,
        REJECTED
    }

    // ------------------------ NESTED CLASS ------------------------

    @Data
    public static class KycReport {

        private String id;
        private String createdBy;
        private LocalDateTime createdAt;
        private String updatedBy;
        private LocalDateTime updatedAt;
        private Boolean deleted;

        private String reportId;
        private LocalDateTime processedAt;

        private ReportStatus status;

        private Double confidence;
        private String summary;
        private String details;

        public enum ReportStatus {
            SUCCESSFUL,
            FAILED
        }
    }
}
