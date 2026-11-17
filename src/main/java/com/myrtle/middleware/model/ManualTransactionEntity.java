package com.myrtle.middleware.model;

import com.myrtle.middleware.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "manual_payment_request_log", indexes = {
        @Index(name = "idx_product_id4Manual_txn", columnList = "product_id"),
        @Index(name = "idx_user_id4Manual_txn", columnList = "user_id")
})
public class ManualTransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String productId;
    private String userId;
    private String userPhoneNumber;
    private String txnAmount;

    @Enumerated(EnumType.STRING)
    private TransactionStatus txnStatus = TransactionStatus.PENDING;
    private Boolean investmentBooked;

    private LocalDateTime txnTime = LocalDateTime.now();

    private Boolean transactionTreated;
    private Integer resolutionTryCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
