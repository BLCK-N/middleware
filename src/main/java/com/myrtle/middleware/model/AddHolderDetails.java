package com.myrtle.middleware.model;

import com.myrtle.middleware.enums.ProofType;
import com.myrtle.middleware.enums.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "add_holder_details")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddHolderDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String address;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "add_holder_proofs", joinColumns = @JoinColumn(name = "add_holder_id"))
    @Column(name = "proof_url")
    private List<String> proof; // use List<String> instead of String[]

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProofType proofType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime createdAt;
}
