package com.myrtle.middleware.dtos.request;

import lombok.*;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InvestmentPayload {

    private InvestmentOrderRequest request;
    private String productId;
    private String pin;
}
