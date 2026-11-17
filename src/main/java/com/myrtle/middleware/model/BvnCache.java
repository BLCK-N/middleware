package com.myrtle.middleware.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity(name = "bvn_validation_cache")
@Table(name = "bvn_validation_cache",
        uniqueConstraints = @UniqueConstraint(name = "uk_bvn_validation_cache_bvn", columnNames = "bvn"),
        indexes = {@Index(name = "idx_bvn", columnList = "bvn")}
)
public class BvnCache {
    @Id
    private String id;

    @Column(unique = true)
    private String bvn;
    private String email;
    private String phone;
    private String gender;
    private String lastName;
    private String firstName;
    private String middleName;
    private String birthdate;
    private String maritalStatus;
    private String stateOfResidence;
    private String stateOfOrigin;
    private String residentialAddress;
    private String nationality;
    private String lgaOfOrigin;
    private boolean verified = false;

    @CreationTimestamp
    private LocalDateTime dateCreated;

    @UpdateTimestamp
    private LocalDateTime updateAt;
}
