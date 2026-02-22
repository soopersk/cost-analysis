package com.company.observability.service;

import com.company.observability.domain.CalculatorRunCost;
import com.company.observability.domain.CostCalculationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Stateless cost calculation engine backed by hardcoded rates.
 *
 * <h3>Approach</h3>
 * Rates are defined as static constants and maps in this class. This is the
 * right call for now: it eliminates a DB round-trip per row, keeps the engine
 * pure and fast, and makes rates trivially visible in code review. When rates
 * need to change, the change is a one-line edit with a clear diff — exactly
 * what you want for something this financially significant.
 *
 * <p>If rates eventually need to be runtime-configurable without redeployment,
 * extract the rate maps into a {@code NodePricingRepository} backed by a
 * {@code node_pricing} table (the schema for this already exists as a future
 * migration). The engine's interface ({@code calculate(run)}) would not change.
 *
 * <h3>Calculation model</h3>
 * <pre>
 *   effectiveWorkers = workerCount               (fixed cluster)
 *                    | autoscaleMax              (autoscaled — conservative estimate)
 *
 *   totalNodes       = 1 (driver) + effectiveWorkers
 *
 *   ── VM cost ──────────────────────────────────────────────────────
 *   driverVmCost     = VM_RATE[driverNodeType] × durationHours
 *   workerVmRate     = spotInstances
 *                        ? VM_RATE[workerNodeType] × SPOT_FRACTION
 *                        : VM_RATE[workerNodeType]
 *   workerVmCost     = workerVmRate × effectiveWorkers × durationHours
 *   vmCost           = driverVmCost + workerVmCost
 *
 *   ── DBU cost ─────────────────────────────────────────────────────
 *   dbuPerNodeHour   = DBU_RATE[workerNodeType]   (driver uses same rate)
 *   dbuMultiplier    = photonEnabled ? PHOTON_DBU_MULTIPLIER : 1.0
 *   dbuUnits         = dbuPerNodeHour × dbuMultiplier × totalNodes × durationHours
 *   dbuCost          = dbuUnits × DBU_PRICE_PER_UNIT
 *
 *   ── Storage ──────────────────────────────────────────────────────
 *   storageCost      = STORAGE_RATE_PER_HOUR × durationHours
 *
 *   ── Total ────────────────────────────────────────────────────────
 *   totalCost        = vmCost + dbuCost + storageCost
 * </pre>
 *
 * <h3>Autoscaling</h3>
 * When {@code autoscale_enabled=true}, actual worker count during the run is
 * unknown from CSV alone. {@code autoscaleMax} is used as the estimate —
 * a conservative upper bound consistent with worst-case billing. This is
 * explicitly noted in {@code cost_calculation_notes} so analysts know the
 * figure is an estimate.
 *
 * <h3>Spot instances</h3>
 * The driver node is always billed at on-demand rates regardless of the
 * {@code spot_instances} flag — Databricks always runs the driver on on-demand.
 * Only worker nodes benefit from the spot discount.
 *
 * <h3>FAILED / TIMEDOUT runs</h3>
 * These ARE priced. The cluster ran and Databricks billed for it up to the
 * point of failure. Only CANCELED runs (where the cluster never started) are
 * zero-cost and are handled as SKIPPED by the calling service.
 */
@Component
@Slf4j
public class CostCalculationEngine {

    // =========================================================================
    // Rate constants
    // =========================================================================

    /**
     * USD per DBU. Matches the contracted rate from the shared JobRunCostService.
     * Standard Jobs Compute list price is $0.15; Photon is $0.20.
     * The $0.176 figure reflects a negotiated blended rate.
     * Update this constant when the Databricks contract is renewed.
     */
    public static final BigDecimal DBU_PRICE_PER_UNIT = new BigDecimal("0.176");

    /**
     * DBU multiplier applied when Photon is enabled.
     * Photon consumes approximately 2× DBU/hr for the same node type, but
     * delivers proportionally higher throughput. The cost impact is real.
     */
    public static final BigDecimal PHOTON_DBU_MULTIPLIER = new BigDecimal("2.0");

    /**
     * Flat managed storage overhead per cluster-hour.
     * Covers Databricks-managed DBFS root, cluster event logs, and audit logs.
     * Does not vary by node type or cluster size.
     */
    public static final BigDecimal STORAGE_RATE_PER_HOUR = new BigDecimal("0.10");

    /**
     * Fraction of on-demand rate paid for spot worker nodes.
     * 0.30 = workers on spot cost 30% of on-demand (70% discount).
     * Driver nodes always pay on-demand regardless of this setting.
     */
    public static final BigDecimal SPOT_FRACTION = new BigDecimal("0.30");

    // =========================================================================
    // VM hourly rates — Azure Linux, eastus2, pay-as-you-go
    //
    // Source: Azure pricing calculator (https://azure.microsoft.com/pricing/)
    // Last verified: 2024-01-01
    //
    // To add a new SKU: add one entry here. The engine logs a warning and
    // assigns $0 if a run references an unknown node type — it will not fail.
    // =========================================================================

    /**
     * Azure VM on-demand hourly rate by node type.
     * All values in USD per node per hour.
     */
    public static final Map<String, BigDecimal> VM_HOURLY_RATES = Map.ofEntries(
            // ── Dasv4 series (general purpose AMD) ───────────────────────────
            Map.entry("Standard_D8as_v4",   new BigDecimal("0.4280")),
            Map.entry("Standard_D16as_v4",  new BigDecimal("0.8560")),
            Map.entry("Standard_D32as_v4",  new BigDecimal("1.7120")),
            // ── Ddsv5 series (general purpose, local SSD) ────────────────────
            Map.entry("Standard_D8ds_v5",   new BigDecimal("0.5040")),
            Map.entry("Standard_D16ds_v5",  new BigDecimal("1.0080")),
            Map.entry("Standard_D32ds_v5",  new BigDecimal("2.0160")),
            // ── Ddsv4 series (general purpose, local SSD) ────────────────────
            Map.entry("Standard_D8ds_v4",   new BigDecimal("0.5040")),
            Map.entry("Standard_D16ds_v4",  new BigDecimal("1.0080")),
            Map.entry("Standard_D32ds_v4",  new BigDecimal("2.0160")),
            // Standard_DBds_v4 is an alternate SKU name for Standard_D8ds_v4
            Map.entry("Standard_DBds_v4",   new BigDecimal("0.5040")),
            // ── Easv4 series (memory-optimised AMD) ──────────────────────────
            Map.entry("Standard_E4as_v4",   new BigDecimal("0.2820")),
            Map.entry("Standard_E8as_v4",   new BigDecimal("0.5640")),
            Map.entry("Standard_E16as_v4",  new BigDecimal("1.1280")),
            // ── Edsv4 series (memory-optimised, local SSD) ───────────────────
            Map.entry("Standard_E4ds_v4",   new BigDecimal("0.3200")),
            Map.entry("Standard_E8ds_v4",   new BigDecimal("0.6400")),
            Map.entry("Standard_E16ds_v4",  new BigDecimal("1.2800")),
            // ── Edsv5 series (memory-optimised, local SSD) ───────────────────
            Map.entry("Standard_E4ds_v5",   new BigDecimal("0.3200")),
            Map.entry("Standard_E8ds_v5",   new BigDecimal("0.6400")),
            Map.entry("Standard_E16ds_v5",  new BigDecimal("1.2800"))
    );

    // =========================================================================
    // DBU rates — Databricks Jobs Compute, per node per hour
    //
    // DBU consumption is a function of vCPU count, not node series:
    //   4  vCPU = 0.75 DBU/hr
    //   8  vCPU = 1.50 DBU/hr
    //   16 vCPU = 3.00 DBU/hr
    //   32 vCPU = 6.00 DBU/hr
    //
    // Photon multiplies this rate by PHOTON_DBU_MULTIPLIER (2.0).
    // Source: https://azure.microsoft.com/pricing/details/databricks/
    // =========================================================================

    /**
     * Standard (non-Photon) DBU consumption rate per node per hour,
     * keyed by Azure VM node type.
     */
    public static final Map<String, BigDecimal> DBU_RATES = Map.ofEntries(
            // ── 4 vCPU nodes ─────────────────────────────────────────────────
            Map.entry("Standard_E4as_v4",   new BigDecimal("0.75")),
            Map.entry("Standard_E4ds_v4",   new BigDecimal("0.75")),
            Map.entry("Standard_E4ds_v5",   new BigDecimal("0.75")),
            // ── 8 vCPU nodes ─────────────────────────────────────────────────
            Map.entry("Standard_D8as_v4",   new BigDecimal("1.50")),
            Map.entry("Standard_D8ds_v5",   new BigDecimal("1.50")),
            Map.entry("Standard_D8ds_v4",   new BigDecimal("1.50")),
            Map.entry("Standard_DBds_v4",   new BigDecimal("1.50")),
            Map.entry("Standard_E8as_v4",   new BigDecimal("1.50")),
            Map.entry("Standard_E8ds_v4",   new BigDecimal("1.50")),
            Map.entry("Standard_E8ds_v5",   new BigDecimal("1.50")),
            // ── 16 vCPU nodes ────────────────────────────────────────────────
            Map.entry("Standard_D16as_v4",  new BigDecimal("3.00")),
            Map.entry("Standard_D16ds_v5",  new BigDecimal("3.00")),
            Map.entry("Standard_D16ds_v4",  new BigDecimal("3.00")),
            Map.entry("Standard_E16as_v4",  new BigDecimal("3.00")),
            Map.entry("Standard_E16ds_v4",  new BigDecimal("3.00")),
            Map.entry("Standard_E16ds_v5",  new BigDecimal("3.00")),
            // ── 32 vCPU nodes ────────────────────────────────────────────────
            Map.entry("Standard_D32as_v4",  new BigDecimal("6.00")),
            Map.entry("Standard_D32ds_v5",  new BigDecimal("6.00")),
            Map.entry("Standard_D32ds_v4",  new BigDecimal("6.00"))
    );

    // ── Precision settings ────────────────────────────────────────────────────

    /** Scale for intermediate BigDecimal arithmetic. */
    private static final int INTERMEDIATE_SCALE = 10;

    /** Scale for stored cost columns (matches NUMERIC(12,4) in the schema). */
    private static final int COST_SCALE = 4;

    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    // =========================================================================
    // Public entry point
    // =========================================================================

    /**
     * Computes all cost components for one run and returns them as an immutable
     * result object. No database access. No side effects.
     *
     * @param run the run to price; must have non-zero durationHours
     * @return    a fully populated {@link CostCalculationResult}
     */
    public CostCalculationResult calculate(CalculatorRunCost run) {

        BigDecimal durationHours = run.getDurationHours();
        int        workerCount   = effectiveWorkerCount(run);
        int        totalNodes    = workerCount + 1; // +1 for driver

        String driverNodeType = run.getDriverNodeType();
        String workerNodeType = coalesceWorkerNodeType(run.getWorkerNodeType(), driverNodeType);
        boolean photon        = Boolean.TRUE.equals(run.getPhotonEnabled());
        boolean spot          = Boolean.TRUE.equals(run.getSpotInstances());

        // ── 1. VM cost ───────────────────────────────────────────────────────

        // Driver: always on-demand. Databricks never runs the driver on spot.
        BigDecimal driverVmRate = lookupVmRate(driverNodeType);
        BigDecimal driverVmCostUsd = driverVmRate
                .multiply(durationHours)
                .setScale(COST_SCALE, ROUNDING);

        // Workers: spot if spot_instances=true, otherwise on-demand.
        BigDecimal workerVmRate = lookupVmRate(workerNodeType);
        if (spot) {
            workerVmRate = workerVmRate.multiply(SPOT_FRACTION)
                    .setScale(INTERMEDIATE_SCALE, ROUNDING);
        }
        BigDecimal workerVmCostUsd = workerVmRate
                .multiply(BigDecimal.valueOf(workerCount))
                .multiply(durationHours)
                .setScale(COST_SCALE, ROUNDING);

        BigDecimal vmCostUsd = driverVmCostUsd.add(workerVmCostUsd);

        // ── 2. DBU cost ──────────────────────────────────────────────────────

        // Use worker node type for the DBU rate — the worker SKU dominates cost
        // and the driver is typically the same type. If they differ, log it.
        BigDecimal dbuPerNodeHour = lookupDbuRate(workerNodeType);
        if (!workerNodeType.equals(driverNodeType)) {
            // Driver has its own DBU rate when it's a different SKU.
            // Average them proportionally: 1 driver + N workers.
            BigDecimal driverDbuPerHour = lookupDbuRate(driverNodeType);
            BigDecimal totalDbuPerHour = driverDbuPerHour
                    .add(dbuPerNodeHour.multiply(BigDecimal.valueOf(workerCount)));
            dbuPerNodeHour = totalDbuPerHour
                    .divide(BigDecimal.valueOf(totalNodes), INTERMEDIATE_SCALE, ROUNDING);
        }

        if (photon) {
            dbuPerNodeHour = dbuPerNodeHour.multiply(PHOTON_DBU_MULTIPLIER)
                    .setScale(INTERMEDIATE_SCALE, ROUNDING);
        }

        BigDecimal dbuUnitsConsumed = dbuPerNodeHour
                .multiply(BigDecimal.valueOf(totalNodes))
                .multiply(durationHours)
                .setScale(COST_SCALE, ROUNDING);

        BigDecimal dbuCostUsd = dbuUnitsConsumed
                .multiply(DBU_PRICE_PER_UNIT)
                .setScale(COST_SCALE, ROUNDING);

        // ── 3. Storage ───────────────────────────────────────────────────────

        BigDecimal storageCostUsd = STORAGE_RATE_PER_HOUR
                .multiply(durationHours)
                .setScale(COST_SCALE, ROUNDING);

        // ── 4. Total ─────────────────────────────────────────────────────────

        BigDecimal totalCostUsd = vmCostUsd
                .add(dbuCostUsd)
                .add(storageCostUsd)
                .setScale(COST_SCALE, ROUNDING);

        // ── 5. Audit notes ───────────────────────────────────────────────────

        String notes = buildNotes(
                driverNodeType, workerNodeType, workerCount, totalNodes,
                durationHours, spot, photon,
                driverVmRate, workerVmRate, dbuPerNodeHour,
                run.getAutoscaleEnabled());

        log.debug("run_id={} dbu={} vm={} storage={} total={}",
                run.getRunId(), dbuCostUsd, vmCostUsd, storageCostUsd, totalCostUsd);

        return CostCalculationResult.builder()
                .runId(run.getRunId())
                .driverVmCostUsd(driverVmCostUsd)
                .workerVmCostUsd(workerVmCostUsd)
                .vmCostUsd(vmCostUsd)
                .dbuUnitsConsumed(dbuUnitsConsumed)
                .dbuCostUsd(dbuCostUsd)
                .storageCostUsd(storageCostUsd)
                .totalCostUsd(totalCostUsd)
                .calculationNotes(notes)
                .build();
    }

    // =========================================================================
    // Rate lookups — log unknown SKUs, return $0, never throw
    // =========================================================================

    /**
     * Returns the on-demand VM hourly rate for the given node type.
     * Unknown types log a warning and return $0 so the run is priced rather
     * than rejected — the analyst can see the zero and investigate the SKU.
     */
    private BigDecimal lookupVmRate(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = VM_HOURLY_RATES.get(nodeType);
        if (rate == null) {
            log.warn("Unknown VM SKU '{}' — VM cost will be $0 for this run. "
                    + "Add an entry to CostCalculationEngine.VM_HOURLY_RATES.", nodeType);
            return BigDecimal.ZERO;
        }
        return rate;
    }

    /**
     * Returns the standard DBU rate per node per hour for the given node type.
     * Unknown types log a warning and return the 8-vCPU default (1.50) rather
     * than $0, because an unknown node type still consumes DBUs — using $0 would
     * silently understate cost.
     */
    private BigDecimal lookupDbuRate(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return new BigDecimal("1.50"); // 8-vCPU default
        }
        BigDecimal rate = DBU_RATES.get(nodeType);
        if (rate == null) {
            log.warn("Unknown node type '{}' in DBU_RATES — using default 1.50 DBU/hr/node. "
                    + "Add an entry to CostCalculationEngine.DBU_RATES.", nodeType);
            return new BigDecimal("1.50");
        }
        return rate;
    }

    // =========================================================================
    // Normalisation helpers
    // =========================================================================

    /**
     * Resolves effective worker count.
     * Fixed cluster: workerCount field.
     * Autoscaled cluster: autoscaleMax (conservative upper bound).
     */
    private int effectiveWorkerCount(CalculatorRunCost run) {
        if (Boolean.TRUE.equals(run.getAutoscaleEnabled())) {
            return run.getAutoscaleMax() != null ? run.getAutoscaleMax() : 1;
        }
        return run.getWorkerCount() != null ? run.getWorkerCount() : 0;
    }

    /** Falls back to driver node type when worker node type is absent. */
    private String coalesceWorkerNodeType(String workerNodeType, String driverNodeType) {
        return (workerNodeType == null || workerNodeType.isBlank())
                ? driverNodeType
                : workerNodeType;
    }

    // =========================================================================
    // Audit trail
    // =========================================================================

    private String buildNotes(
            String driverNodeType, String workerNodeType,
            int workerCount, int totalNodes,
            BigDecimal durationHours, boolean spot, boolean photon,
            BigDecimal driverVmRate, BigDecimal workerVmRate,
            BigDecimal effectiveDbuPerNodeHour, Boolean autoscaleEnabled) {

        return ("driver=%s @$%.4f/hr | workers=%d×%s @$%.4f/hr%s | "
                + "dbu=%.4f/node/hr%s × %d nodes × $%.4f/dbu | "
                + "duration_hr=%.4f | workers=%s")
                .formatted(
                        driverNodeType,
                        driverVmRate,
                        workerCount,
                        workerNodeType,
                        workerVmRate,
                        spot ? "(spot)" : "(on-demand)",
                        effectiveDbuPerNodeHour,
                        photon ? "×photon" : "",
                        totalNodes,
                        DBU_PRICE_PER_UNIT,
                        durationHours,
                        Boolean.TRUE.equals(autoscaleEnabled) ? "autoscale_max" : "fixed");
    }
}