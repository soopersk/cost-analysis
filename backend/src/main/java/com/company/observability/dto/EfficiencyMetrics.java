package com.company.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class EfficiencyMetrics {

    private Double spotInstanceUsagePct;
    private Double photonAdoptionPct;
    private Double failureRatePct;
}
