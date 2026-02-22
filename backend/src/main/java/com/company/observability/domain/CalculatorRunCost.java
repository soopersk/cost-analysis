package com.company.observability.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Single unified domain object representing one Databricks calculator job run,
 * including its raw cluster metadata and associated costs.
 *
 * <h3>Frequency segregation</h3>
 * The same {@code calculatorId} can be scheduled at both DAILY and MONTHLY
 * cadences. {@code frequency} tags every row at ingest time. All analytics
 * queries require {@code frequency} as a mandatory grouping or filter dimension
 * so that daily and monthly cost profiles are never mixed.
 *
 * <h3>reporting_date vs start_time</h3>
 * {@code startTime} — when the Databricks job physically executed (UTC).
 * {@code reportingDate} — the business date this run's output pertains to.
 *   For DAILY runs this is typically the prior calendar day.
 *   For MONTHLY runs this is typically the last day of the covered month.
 * These two dates can differ and must both be stored explicitly.
 *
 * <p>Maps 1:1 to the {@code calculator_run_costs} table (V4 migration).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatorRunCost {

    // -------------------------------------------------------------------------
    // Surrogate PK
    // -------------------------------------------------------------------------

    /** Auto-generated surrogate key. */
    private Long id;

    // -------------------------------------------------------------------------
    // Databricks run identifiers
    // -------------------------------------------------------------------------

    /**
     * Databricks run ID — globally unique per execution, sourced directly from
     * the CSV {@code run_id} column. Natural unique key for idempotent loads.
     */
    private Long runId;

    /** Databricks job ID ({@code job_id}). */
    private Long jobId;

    /** Human-readable Databricks job name ({@code job_name}). */
    private String jobName;

    // -------------------------------------------------------------------------
    // Calculator identity
    // -------------------------------------------------------------------------

    /** From cluster custom_tags["task-id"]. */
    private String calculatorId;

    /** From cluster custom_tags["task-name"]. */
    private String calculatorName;

    // -------------------------------------------------------------------------
    // Scheduling dimensions — the two new fields
    // -------------------------------------------------------------------------

    /**
     * Scheduling cadence: DAILY or MONTHLY.
     *
     * <p>Mandatory. Every analytics query partitions by this field so that
     * DAILY and MONTHLY cost profiles for the same calculator are always
     * aggregated and compared separately.
     */
    private RunFrequency frequency;

    /**
     * The business date this run's output pertains to.
     *
     * <p>Distinct from {@code startTime} (which is when the Databricks job
     * actually ran). For DAILY runs: typically the prior calendar day.
     * For MONTHLY runs: typically the last calendar day of the covered month.
     *
     * <p>Used as the primary time axis in all reporting queries instead of
     * {@code startTime::DATE} so that late-running jobs don't shift into the
     * wrong reporting period.
     */
    private LocalDate reportingDate;

    // -------------------------------------------------------------------------
    // Cluster identity
    // -------------------------------------------------------------------------

    /** Ephemeral cluster ID created for this run. */
    private String clusterId;

    // -------------------------------------------------------------------------
    // Run timing
    // -------------------------------------------------------------------------

    /** Job run start time (UTC). */
    private LocalDateTime startTime;

    /** Job run end time (UTC). */
    private LocalDateTime endTime;

    /** Elapsed time in whole seconds. */
    private Integer durationSeconds;

    /** Elapsed time in decimal hours, pre-computed by the Databricks notebook. */
    private BigDecimal durationHours;

    // -------------------------------------------------------------------------
    // Run outcome
    // -------------------------------------------------------------------------

    /** Terminal run status: SUCCESS, FAILED, TIMEDOUT, CANCELED. */
    private String status;

    // -------------------------------------------------------------------------
    // Cluster configuration
    // -------------------------------------------------------------------------

    /** Driver node type, e.g. "Standard_DS3_v2". */
    private String driverNodeType;

    /** Worker node type, e.g. "Standard_DS3_v2". */
    private String workerNodeType;

    /**
     * Fixed worker count when autoscaling is disabled.
     * Null when {@code autoscaleEnabled = true}.
     */
    private Integer workerCount;

    /** Whether Azure Spot instances were used for worker nodes. */
    private Boolean spotInstances;

    /** Whether the Photon vectorised query engine was enabled. */
    private Boolean photonEnabled;

    // -------------------------------------------------------------------------
    // Runtime metadata
    // -------------------------------------------------------------------------

    /** Databricks Runtime version string, e.g. "14.3.x-scala2.12". */
    private String sparkVersion;

    /** Runtime engine label: "STANDARD" or "PHOTON". */
    private String runtimeEngine;

    /** How the cluster was launched: "JOB", "UI", or "API". */
    private String clusterSource;

    /** Workload classification: "Jobs Compute" or "All-Purpose Compute". */
    private String workloadType;

    /** Azure region slug, e.g. "eastus2". */
    private String region;

    // -------------------------------------------------------------------------
    // Autoscaling
    // -------------------------------------------------------------------------

    /** Whether autoscaling was configured for this cluster. */
    private Boolean autoscaleEnabled;

    /** Autoscale minimum worker count; null when autoscaling is off. */
    private Integer autoscaleMin;

    /** Autoscale maximum worker count; null when autoscaling is off. */
    private Integer autoscaleMax;

    // -------------------------------------------------------------------------
    // Custom-tag provenance
    // -------------------------------------------------------------------------

    /** From cluster custom_tags["task-version"]. */
    private String taskVersion;

    /** From cluster custom_tags["context-id"]. */
    private String contextId;

    /** From cluster custom_tags["megdp-run-id"]. */
    private String megdpRunId;

    /** From cluster custom_tags["tenant-abb"]. */
    private String tenantAbb;

    // -------------------------------------------------------------------------
    // Costs (USD) — as reported by the Databricks notebook
    // -------------------------------------------------------------------------

    /** Databricks Unit (DBU) licence cost. */
    private BigDecimal dbuCostUsd;

    /**
     * Virtual-machine compute cost (single aggregate figure).
     * Driver/worker split is a v2 concern.
     */
    private BigDecimal vmCostUsd;

    /** Cloud storage cost. */
    private BigDecimal storageCostUsd;

    /** Total cost: dbu + vm + storage. */
    private BigDecimal totalCostUsd;

    // -------------------------------------------------------------------------
    // Audit
    // -------------------------------------------------------------------------

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Domain helpers
    // -------------------------------------------------------------------------

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }

    public boolean isDaily() {
        return RunFrequency.DAILY.equals(frequency);
    }

    public boolean isMonthly() {
        return RunFrequency.MONTHLY.equals(frequency);
    }

    /**
     * Cost per minute of execution. Returns ZERO for failed/zero-duration runs.
     */
    public BigDecimal getCostPerMinute() {
        if (durationSeconds == null || durationSeconds == 0 || totalCostUsd == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal minutes = BigDecimal.valueOf(durationSeconds)
                .divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
        return totalCostUsd.divide(minutes, 6, RoundingMode.HALF_UP);
    }

    /**
     * Effective worker count for display.
     * Returns the fixed {@code workerCount} for static clusters, or
     * {@code autoscaleMax} as a proxy for autoscaled clusters.
     */
    public Integer getEffectiveWorkerCount() {
        return Boolean.TRUE.equals(autoscaleEnabled) ? autoscaleMax : workerCount;
    }
}
