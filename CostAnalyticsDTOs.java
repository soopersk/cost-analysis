package com.company.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Main dashboard response
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostDashboardResponse {
    private List<DailyCostTrend> dailyCosts;
    private List<CalculatorCostSummary> calculatorCosts;
    private List<RecentRunCost> recentRuns;
    private CostBreakdown costBreakdown;
    private EfficiencyMetrics efficiency;
    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
}

// Daily cost trend
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCostTrend {
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    
    private BigDecimal totalCost;
    private BigDecimal dbuCost;
    private BigDecimal vmCost;
    private BigDecimal storageCost;
    private BigDecimal networkCost;
}

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
                .divide(BigDecimal.valueOf(totalRuns), 2, BigDecimal.ROUND_HALF_UP);
    }
    
    public BigDecimal getSpotUsagePercent() {
        if (totalRuns == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(spotInstanceRuns)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRuns), 2, BigDecimal.ROUND_HALF_UP);
    }
    
    public BigDecimal getPhotonAdoptionPercent() {
        if (totalRuns == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(photonEnabledRuns)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRuns), 2, BigDecimal.ROUND_HALF_UP);
    }
}

// Recent run with cost details
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentRunCost {
    private String runId;
    private String calculatorId;
    private String calculatorName;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
    
    private Integer durationSeconds;
    private String status;
    private BigDecimal dbuCost;
    private BigDecimal vmCost;
    private BigDecimal storageCost;
    private BigDecimal totalCost;
    private BigDecimal workerCount;
    private Boolean spotInstance;
    private Boolean photonEnabled;
    private Boolean isRetry;
    private Integer attemptNumber;
    
    // Computed properties
    public BigDecimal getCostPerMinute() {
        if (durationSeconds == null || durationSeconds == 0) return BigDecimal.ZERO;
        return totalCost.multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(durationSeconds), 6, BigDecimal.ROUND_HALF_UP);
    }
}

// Cost breakdown by component
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostBreakdown {
    private BigDecimal dbuCost;
    private BigDecimal vmCost;
    private BigDecimal storageCost;
    private BigDecimal networkCost;
    private BigDecimal totalCost;
    
    public List<CostBreakdownItem> getBreakdownItems() {
        return List.of(
            new CostBreakdownItem("DBU Compute", dbuCost, "#3b82f6"),
            new CostBreakdownItem("VM Infrastructure", vmCost, "#8b5cf6"),
            new CostBreakdownItem("Storage", storageCost, "#10b981"),
            new CostBreakdownItem("Network", networkCost, "#f59e0b")
        );
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class CostBreakdownItem {
    private String name;
    private BigDecimal value;
    private String color;
}

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

// Cost anomaly
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostAnomaly {
    private String runId;
    private String calculatorId;
    private String calculatorName;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;
    
    private BigDecimal actualCost;
    private BigDecimal averageCost;
    private BigDecimal costMultiplier;
    private String status;
    private Integer durationSeconds;
    
    // Computed
    public BigDecimal getVariancePercent() {
        if (averageCost.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return actualCost.subtract(averageCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(averageCost, 2, BigDecimal.ROUND_HALF_UP);
    }
}
