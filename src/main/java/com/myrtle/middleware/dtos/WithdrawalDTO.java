package com.myrtle.middleware.dtos;

import com.myrtle.middleware.model.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class WithdrawalDTO {
    private String amount;
    private String channel;
    private String product;
    private LocalDateTime transactionDateTime;
}
