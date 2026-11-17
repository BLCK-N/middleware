package com.myrtle.middleware.dtos;

import com.myrtle.middleware.model.Investment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentDTO {

    private String id;
    private String productId;
    private String accountId;
    private Investment.OrderType orderType;
    private String marketCode;
    private String secId;
    private String requestedQty;
    private String requestedVal;
    private String price;
    private LocalDateTime requestDate;
    private Investment.Currency currency;
    private Investment.InvestmentStatus investmentStatus;
    private String notes;
    private Boolean autoApprove;
    private Boolean autoPayRedemption;
    private Investment.Channel channel;
    private String refCode;
    private LocalDateTime createdAt;
    private String userId;  // avoid lazy loading the full User entity
}
