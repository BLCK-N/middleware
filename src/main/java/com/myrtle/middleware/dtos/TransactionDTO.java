package com.myrtle.middleware.dtos;

import com.myrtle.middleware.enums.TransactionAction;
import com.myrtle.middleware.enums.TransactionStatus;
import com.myrtle.middleware.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDTO {

    private String id;
    private TransactionType type;
    private BigDecimal amount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private TransactionStatus status;
    private TransactionAction action;
    private String narration;
    private String txRef;
    private String source;

    // Reference IDs instead of whole entities
    private String userId;
    private String investmentId;
}
