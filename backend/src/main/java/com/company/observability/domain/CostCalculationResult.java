package com.company.observability.domain;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Immutable result of pricing one calculator run.
 *
 * <p>Produced by {@link com.company.observability.service.CostCalculationEngine}
 * and written back to {@code calculator_run_costs} by
 * {@link com.company.observability.repository.CostCalculationRepository}.
 *
 * <p>Carries split cost components (driver VM, worker VM, DBU, storage) so the
 * analytics dashboard can show a meaningful cost breakdown rather than just
 * a single total figure.
 */
@Value
@Builder
public class CostCalculationResult {

    Long runId;

    // ── VM costs ──────────────────────────────────────────────────────────────

    /** On-demand cost for the single driver node: driverRate × durationHours. */
    BigDecimal driverVmCostUsd;

    /**
     * Cost for all worker nodes: workerRate × workerCount × durationHours.
     * If spot_instances=true, workerRate is reduced by the spot discount.
     */
    BigDecimal workerVmCostUsd;

    /** driverVmCostUsd + workerVmCostUsd. */
    BigDecimal vmCostUsd;

    // ── DBU costs ─────────────────────────────────────────────────────────────

    /**
     * Total DBU units consumed across all nodes:
     * dbuPerNodeHour × totalNodes × durationHours.
     */
    BigDecimal dbuUnitsConsumed;

    /** dbuUnitsConsumed × DBU_PRICE_PER_UNIT. */
    BigDecimal dbuCostUsd;

    // ── Storage ───────────────────────────────────────────────────────────────

    /**
     * Managed storage overhead per cluster-hour.
     * Covers DBFS root, audit logs, and cluster event logs.
     */
    BigDecimal storageCostUsd;

    // ── Total ─────────────────────────────────────────────────────────────────

    /** vmCostUsd + dbuCostUsd + storageCostUsd. */
    BigDecimal totalCostUsd;

    // ── Audit snapshot ────────────────────────────────────────────────────────

    /**
     * Human-readable snapshot of the rates used for this calculation.
     * Stored in {@code cost_calculation_notes} so any computed figure is
     * auditable without re-running the engine.
     */
    String calculationNotes;
}