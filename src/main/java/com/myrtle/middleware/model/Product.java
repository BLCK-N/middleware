    package com.myrtle.middleware.model;

    import jakarta.persistence.*;
            import lombok.AllArgsConstructor;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;

    import java.util.ArrayList;
    import java.util.List;

    @Entity
    @Table(name = "products")
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public class Product {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private String id;
        private String code;
        private String label;
        private String description;
        private String[] benefits;
        private String pdId;
        private String profitPercentage;
        private Integer[] maturityDates;
        private String productType;

        // Bank account details for transfers
        private String bankAccountName;
        private String bankAccountNumber;
        private String bankName;
        private String currency;

        @Column(name = "initial_limit")
        private String initialLimit;

        private Boolean isInitialLimitPass = false;

        @Column(name = "minimum_limit")
        private String minimumLimit;

        // Payment method restrictions
        private Boolean flutterwaveOnly = false; // If true, only Flutterwave payment allowed (no wallet)
    }
