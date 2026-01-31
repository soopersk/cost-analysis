package com.company.observability.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "calculator_run_costs", schema = "finops")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatorRunCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cost_id")
    private Long costId;

    @Column(name = "calculator_run_id", nullable = false, length = 100)
    private String calculatorRunId;

    @Column(name = "reporting_date", nullable = false)
    private LocalDate reportingDate;

    @Column(name = "calculator_id", nullable = false, length = 100)
    private String calculatorId;

    @Column(name = "calculator_name", nullable = false, length = 255)
    private String calculatorName;

    @Column(name = "databricks_run_id", nullable = false)
    private Long databricksRunId;

    @Column(name = "cluster_id", nullable = false, length = 100)
    private String clusterId;

    // Cluster configuration
    @Column(name = "driver_node_type", length = 50)
    private String driverNodeType;

    @Column(name = "worker_node_type", length = 50)
    private String workerNodeType;

    @Column(name = "min_workers")
    private Integer minWorkers;

    @Column(name = "max_workers")
    private Integer maxWorkers;

    @Column(name = "avg_worker_count", precision = 6, scale = 2)
    private BigDecimal avgWorkerCount;

    @Column(name = "peak_worker_count")
    private Integer peakWorkerCount;

    @Column(name = "spot_instance_used")
    private Boolean spotInstanceUsed;

    @Column(name = "photon_enabled")
    private Boolean photonEnabled;

    // Runtime metrics
    @Column(name = "run_start_time", nullable = false)
    private LocalDateTime runStartTime;

    @Column(name = "run_end_time", nullable = false)
    private LocalDateTime runEndTime;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "run_status", nullable = false, length = 20)
    private String runStatus;

    @Column(name = "is_retry")
    private Boolean isRetry;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    // DBU costs
    @Column(name = "dbu_units_consumed", precision = 12, scale = 4)
    private BigDecimal dbuUnitsConsumed;

    @Column(name = "dbu_unit_price", precision = 8, scale = 4)
    private BigDecimal dbuUnitPrice;

    @Column(name = "dbu_cost_usd", precision = 12, scale = 4)
    private BigDecimal dbuCostUsd;

    @Column(name = "dbu_sku", length = 100)
    private String dbuSku;

    // VM costs
    @Column(name = "vm_driver_cost_usd", precision = 12, scale = 4)
    private BigDecimal vmDriverCostUsd;

    @Column(name = "vm_worker_cost_usd", precision = 12, scale = 4)
    private BigDecimal vmWorkerCostUsd;

    @Column(name = "vm_total_cost_usd", precision = 12, scale = 4)
    private BigDecimal vmTotalCostUsd;

    @Column(name = "vm_node_hours", precision = 12, scale = 2)
    private BigDecimal vmNodeHours;

    // Storage costs
    @Column(name = "storage_input_gb", precision = 12, scale = 2)
    private BigDecimal storageInputGb;

    @Column(name = "storage_output_gb", precision = 12, scale = 2)
    private BigDecimal storageOutputGb;

    @Column(name = "storage_temp_gb", precision = 12, scale = 2)
    private BigDecimal storageTempGb;

    @Column(name = "storage_cost_usd", precision = 12, scale = 4)
    private BigDecimal storageCostUsd;

    // Network costs
    @Column(name = "network_egress_gb", precision = 12, scale = 2)
    private BigDecimal networkEgressGb;

    @Column(name = "network_cost_usd", precision = 12, scale = 4)
    private BigDecimal networkCostUsd;

    // Total cost (computed)
    @Column(name = "total_cost_usd", precision = 12, scale = 4)
    private BigDecimal totalCostUsd;

    // Allocation metadata
    @Column(name = "allocation_method", nullable = false, length = 50)
    private String allocationMethod;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "concurrent_runs_on_cluster")
    private Integer concurrentRunsOnCluster;

    @Column(name = "cluster_utilization_pct", precision = 5, scale = 2)
    private BigDecimal clusterUtilizationPct;

    // Data lineage
    @Column(name = "calculation_version", nullable = false, length = 20)
    private String calculationVersion;

    @Column(name = "calculation_timestamp", nullable = false)
    private LocalDateTime calculationTimestamp;

    @Column(name = "calculated_by", length = 100)
    private String calculatedBy;

    // Manual adjustments
    @Column(name = "manual_adjustment_usd", precision = 12, scale = 4)
    private BigDecimal manualAdjustmentUsd;

    @Column(name = "manual_adjustment_reason")
    private String manualAdjustmentReason;

    @Column(name = "adjusted_by", length = 100)
    private String adjustedBy;

    @Column(name = "adjusted_at")
    private LocalDateTime adjustedAt;

    // Audit timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
