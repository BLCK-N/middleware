package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pending_payments")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PendingPayment {

    // ===== Primary Key =====
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // ===== Investment Snapshot Fields (Pre-Payment) =====
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column
    private String transRef; // Transaction Reference for the investment

    @Column(nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Column(nullable = false)
    private String marketCode;

    @Column(nullable = false)
    private String secId;

    @Column
    private String requestedQty;

    @Column
    private String requestedVal;

    @Column
    private String price;

    @Column(nullable = false)
    private LocalDateTime requestDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column
    private String notes;

    @Column(nullable = false)
    private Boolean autoApprove = false;

    @Column
    private Boolean autoPayRedemption = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column
    private String refCode;

    // ===== Payment Control Fields =====
    @Column(nullable = false, unique = true)
    private String txRef;

    @Column(nullable = false)
    private String amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(unique = true)
    private String flutterwaveTransactionId;

    // ===== Flutterwave Metadata Fields =====
    @Column
    private String chargedAmount;

    @Column
    private String paymentType;

    @Column
    private String appFee;

    @Column(columnDefinition = "TEXT")
    private String processorResponse;

    @Column
    private String paymentChannel;

    @Column
    private String customerEmail;

    // ===== Audit Fields =====
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime completedAt;

    // ===== Relationships =====
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    // ===== Enums (Local & Self-Contained) =====
    public enum OrderType {
        SUBSCRIPTION,
        REDEMPTION
    }

    public enum Channel {
        NA,
        WEB,
        MOBILE,
        API
    }

    public enum Currency {
        NGN,
        USD,
        EUR,
        GBP
    }

    public enum PaymentStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}
