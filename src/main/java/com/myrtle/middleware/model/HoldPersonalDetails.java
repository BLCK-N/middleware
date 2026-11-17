package com.myrtle.middleware.model;

import com.myrtle.middleware.enums.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "hold_details")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class HoldPersonalDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "middle_name", nullable = false)
    private String middleName;

    @Column(name = "phoneNumber", nullable = false, unique = false)
    private String phoneNumber;

    @Column(name = "reason_for_change", nullable = false)
    private String reason_for_change;

    @Column(name = "proof_image", nullable = false)
    private String[] proof_image;

    @Column(name = "status", nullable = false)
    private Status status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


}
