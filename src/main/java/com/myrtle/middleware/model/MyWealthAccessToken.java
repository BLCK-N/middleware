package com.myrtle.middleware.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Data
@Entity(name = "mywealth_access_token")
public class MyWealthAccessToken {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(columnDefinition = "TEXT")
    private String tokenType;

    @Column(columnDefinition = "TEXT")
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    @Column(columnDefinition = "TEXT")
    private String scope;

    private int expiresIn;

    private int refreshExpiresIn;

    private Instant expiresAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
