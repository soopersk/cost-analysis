package com.company.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Calculator cost summary
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalculatorCostSummary {
    private String calculatorId;
    private String calculatorName;
    private Long totalRuns;
    private BigDecimal totalCost;
    private BigDecimal avgCostPerRun;
    private BigDecimal dbuCost;
    private BigDecimal vmCost;
    private BigDecimal storageCost;
    private BigDecimal avgConfidenceScore;
    private Long successfulRuns;
    private Long failedRuns;
    private Long retryRuns;
    private Long spotInstanceRuns;
    private Long photonEnabledRuns;
    
    // Computed properties
    public BigDecimal getEfficiencyScore() {
        if (totalRuns == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(successfulRuns)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRuns), 2, RoundingMode.HALF_UP);
    }
    
    public BigDecimal getSpotUsagePercent() {
        if (totalRuns == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(spotInstanceRuns)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRuns), 2, RoundingMode.HALF_UP);
    }
    
    public BigDecimal getPhotonAdoptionPercent() {
        if (totalRuns == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(photonEnabledRuns)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRuns), 2, RoundingMode.HALF_UP);
    }
}

