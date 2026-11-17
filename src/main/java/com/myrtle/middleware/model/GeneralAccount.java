package com.myrtle.middleware.model;

import com.myrtle.middleware.enums.Currency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "general_accounts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GeneralAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_code")
    private String productCode;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
