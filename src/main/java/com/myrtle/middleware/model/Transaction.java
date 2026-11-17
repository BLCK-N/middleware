package com.myrtle.middleware.model;

import com.myrtle.middleware.enums.TransactionAction;
import com.myrtle.middleware.enums.TransactionStatus;
import com.myrtle.middleware.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TransactionType type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"transactions"})  // prevent back reference to transactions
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_id")
    @JsonIgnoreProperties({"transactions"})  // prevent back reference to transactions
    private Investment investment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionAction action;

    @Column(length = 255)
    private String narration;

    @Column(name = "txRef")
    private String txRef;

    @Column(length = 100)
    private String source;
}