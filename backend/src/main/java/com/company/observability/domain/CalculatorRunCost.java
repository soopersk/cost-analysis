package com.company.observability.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatorRunCost {

    private Long costId;

    private String calculatorRunId;
    private LocalDate reportingDate;

    private String calculatorId;
    private String calculatorName;

    private Long databricksRunId;
    private String clusterId;

    // Cluster configuration
    private String driverNodeType;
    private String workerNodeType;
    private Integer minWorkers;
    private Integer maxWorkers;
    private BigDecimal avgWorkerCount;
    private Integer peakWorkerCount;
    private Boolean spotInstanceUsed;
    private Boolean photonEnabled;

    // Runtime metrics
    private LocalDateTime runStartTime;
    private LocalDateTime runEndTime;
    private Integer durationSeconds;
    private String runStatus;
    private Boolean isRetry;
    private Integer attemptNumber;

    // DBU costs
    private BigDecimal dbuUnitsConsumed;
    private BigDecimal dbuUnitPrice;
    private BigDecimal dbuCostUsd;
    private String dbuSku;

    // VM costs
    private BigDecimal vmDriverCostUsd;
    private BigDecimal vmWorkerCostUsd;
    private BigDecimal vmTotalCostUsd;
    private BigDecimal vmNodeHours;

    // Storage costs
    private BigDecimal storageInputGb;
    private BigDecimal storageOutputGb;
    private BigDecimal storageTempGb;
    private BigDecimal storageCostUsd;

    // Network costs
    private BigDecimal networkEgressGb;
    private BigDecimal networkCostUsd;

    // Total cost
    private BigDecimal totalCostUsd;

    // Allocation metadata
    private String allocationMethod;
    private BigDecimal confidenceScore;
    private Integer concurrentRunsOnCluster;
    private BigDecimal clusterUtilizationPct;

    // Data lineage
    private String calculationVersion;
    private LocalDateTime calculationTimestamp;
    private String calculatedBy;

    // Manual adjustments
    private BigDecimal manualAdjustmentUsd;
    private String manualAdjustmentReason;
    private String adjustedBy;
    private LocalDateTime adjustedAt;

    // Audit timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Helper methods
    public BigDecimal getCostPerMinute() {
        if (durationSeconds == null || durationSeconds == 0 || totalCostUsd == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal durationMinutes = BigDecimal.valueOf(durationSeconds).divide(BigDecimal.valueOf(60), 6, BigDecimal.ROUND_HALF_UP);
        return totalCostUsd.divide(durationMinutes, 6, RoundingMode.HALF_UP);
    }

    public BigDecimal getCostPerGbProcessed() {
        if (totalCostUsd == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalGb = BigDecimal.ZERO;
        if (storageInputGb != null) totalGb = totalGb.add(storageInputGb);
        if (storageOutputGb != null) totalGb = totalGb.add(storageOutputGb);

        if (totalGb.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalCostUsd.divide(totalGb, 6, RoundingMode.HALF_UP);
    }

    public boolean isHighCost() {
        return totalCostUsd != null && totalCostUsd.compareTo(BigDecimal.valueOf(50)) > 0;
    }

    public boolean isFailed() {
        return "FAILED".equals(runStatus);
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(runStatus);
    }
}

