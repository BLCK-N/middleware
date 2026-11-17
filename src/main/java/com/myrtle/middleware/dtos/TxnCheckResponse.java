package com.myrtle.middleware.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.myrtle.middleware.enums.TransactionStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TxnCheckResponse {
    private String transactionId;
    private String productId;
    private String txnAmount;
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    private LocalDateTime txnTime;
    private Boolean transactionTreated;
    private String txnReference;
    private Boolean investmentBooked;
}
