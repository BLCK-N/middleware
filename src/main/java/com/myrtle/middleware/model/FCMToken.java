package com.myrtle.middleware.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fcm_tokens")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FCMToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String token;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
