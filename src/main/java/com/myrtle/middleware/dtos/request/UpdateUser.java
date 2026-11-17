package com.myrtle.middleware.dtos.request;

import lombok.Data;

@Data
public class UpdateUser {
    private String accountNumber;
    private String nin;
    private String bvn;
    private String email;
}
