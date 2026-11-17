package com.myrtle.middleware.dtos.response;

import lombok.Data;

import java.util.List;

@Data
public class FinIdLookupResponse {

    private String finIdNo;
    private boolean found;
    private int count;
    private List<ClientSummary> clients;
}
