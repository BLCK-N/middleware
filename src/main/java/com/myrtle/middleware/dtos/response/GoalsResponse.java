package com.myrtle.middleware.dtos.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class GoalsResponse {
    private String id;
    private String code;
    private String label;
}
