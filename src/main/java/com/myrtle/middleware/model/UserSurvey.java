package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_survey")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String persona;

    private String recommendation;

    private String channel;

    @Column(columnDefinition = "TEXT", length = 2000)
    private String responseJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;
}
