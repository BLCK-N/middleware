package com.myrtle.middleware.dtos.response;

import lombok.Data;

@Data
public class KycReport {
    private String id;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
    private boolean deleted;
    private String reportId;
    private String processedAt;
    private String status;
    private double confidence;
    private String summary;
    private String details;
}
