package com.myrtle.middleware.dtos.response;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StatsResponse {
    private double totalCredit;
    private double totalDebit;
    private double totalMain;
}
