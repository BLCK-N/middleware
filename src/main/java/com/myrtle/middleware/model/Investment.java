package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "investments")
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;  // The product being invested in

    @Column(nullable = false)
    private String accountId; // Investment account reference

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;  // SUBSCRIPTION or REDEMPTION

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

//    @Column(nullable = false)
//    private String recordId;

    @Column(nullable = false)
    private LocalDateTime requestDate;  // stored properly as LocalDateTime

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvestmentStatus investmentStatus = InvestmentStatus.RUNNING;

    @Column(name = "notes")
    private String notes;

    @Column(nullable = false)
    private Boolean autoApprove = false;

    @Column
    private Boolean autoPayRedemption = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(name = "refCode")
    private String refCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== Relationships =====
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;  // Who made the investment

    // For one-to-many, each Investment can have multiple related transactions
    @OneToMany(mappedBy = "investment", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Transaction> transactions;

    // ===== Enums from InvestmentOrderRequest =====
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

    public enum InvestmentStatus {
        RUNNING,
        TERMINATED,
        REDEEMED,
        FAILED,
        SUBSCRIPTION
    }
}
