package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateKycDocumentRequest {

    private String clientId;
    private String recordType;
    private String idType;
    private String idNo;
    private String fileUrl;
    private String fileName;
    private String fileType;
}
