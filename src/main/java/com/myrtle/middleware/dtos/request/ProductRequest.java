package com.myrtle.middleware.dtos.request;

import lombok.Data;

@Data
public class ProductRequest {

    private String productType;
    private String branchId;
    private String productId;
}
