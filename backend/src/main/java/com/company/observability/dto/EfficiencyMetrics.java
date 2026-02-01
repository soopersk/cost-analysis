package com.company.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Efficiency metrics
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EfficiencyMetrics {
    private Double spotInstanceUsage;      // Percentage
    private Double photonAdoption;         // Percentage
    private Double avgClusterUtilization;  // Percentage
    private Double retryRate;              // Percentage
    private Double failureRate;            // Percentage
}
