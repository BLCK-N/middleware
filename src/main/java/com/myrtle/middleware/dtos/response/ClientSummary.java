package com.myrtle.middleware.dtos.response;

import lombok.Data;

import java.util.UUID;

@Data
public class ClientSummary {

    private UUID id;
    private String code;
    private String label;
    private String status;
    private String clientType;
    private String email;
    private String mobileNo;
}

