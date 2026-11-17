package com.myrtle.middleware.dtos.response;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class AdminResponse<T> {

    private String message;
    private String code;
    private T data;
    private Object meta;

}
