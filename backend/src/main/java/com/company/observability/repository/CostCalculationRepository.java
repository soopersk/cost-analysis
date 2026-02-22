package com.company.observability.repository;

import com.company.observability.domain.CalculatorRunCost;
import com.company.observability.domain.CostCalculationResult;
import com.company.observability.domain.RunFrequency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles all database reads and writes for the cost calculation lifecycle.
 *
 * <p>Intentionally separate from {@link CalculatorRunCostRepository} so the
 * calculation concern has a clear boundary. Every state transition atomically
 * updates {@code cost_calculation_status}, {@code cost_calculated_at}, and
 * {@code cost_calculation_notes} together — a row is never left half-updated.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class CostCalculationRepository {

    private final JdbcTemplate jdbc;

    // =========================================================================
    // READ — work queue
    // =========================================================================

    /**
     * Fetches up to {@code batchSize} PENDING rows ordered by id (oldest first).
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} ensures concurrent callers (scheduler
     * + ad-hoc trigger) each get a non-overlapping slice — no row is priced twice.
     */
    public List<CalculatorRunCost> fetchPendingBatch(int batchSize) {
        // language=sql
        String sql = """
            SELECT
                cost_id, run_id, job_id, job_name,
                calculator_id, calculator_name,
                frequency, reporting_date,
                cluster_id,
                start_time, end_time, duration_seconds, duration_hours,
                status,
                driver_node_type, worker_node_type, worker_count,
                spot_instances, photon_enabled,
                spark_version, runtime_engine, cluster_source, workload_type, region,
                autoscale_enabled, autoscale_min, autoscale_max,
                task_version, context_id, megdp_run_id, tenant_abb
            FROM calculator_run_costs
            WHERE cost_calculation_status = 'PENDING'
            ORDER BY cost_id ASC
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;
        return jdbc.query(sql, pendingRowMapper(), batchSize);
    }

    public long countPending() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM calculator_run_costs WHERE cost_calculation_status = 'PENDING'",
                Long.class);
        return n != null ? n : 0L;
    }

    public long countFailed() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM calculator_run_costs WHERE cost_calculation_status = 'FAILED'",
                Long.class);
        return n != null ? n : 0L;
    }

    // =========================================================================
    // WRITE — status transitions
    // =========================================================================

    /**
     * Batch-writes all computed cost components and marks rows CALCULATED.
     *
     * <p>Writes all six cost columns — including the driver/worker VM split —
     * so the analytics layer can show a detailed breakdown without recomputing.
     *
     * <p>The {@code WHERE cost_calculation_status = 'PENDING'} predicate is a
     * safety guard: if two threads somehow reach the same row, the second UPDATE
     * silently affects 0 rows rather than overwriting a valid result.
     */
    public void markCalculatedBatch(List<CostCalculationResult> results) {
        if (results.isEmpty()) return;

        // language=sql
        String sql = """
            UPDATE calculator_run_costs SET
                dbu_cost_usd            = ?,
                vm_cost_usd             = ?,
                storage_cost_usd        = ?,
                total_cost_usd          = ?,
                cost_calculation_status = 'CALCULATED',
                cost_calculated_at      = ?,
                cost_calculation_notes  = ?,
                updated_at              = NOW()
            WHERE run_id = ?
              AND cost_calculation_status = 'PENDING'
            """;

        LocalDateTime now = LocalDateTime.now();
        jdbc.batchUpdate(sql, results, results.size(), (ps, r) -> {
            ps.setBigDecimal(1, r.getDbuCostUsd());
            ps.setBigDecimal(2, r.getVmCostUsd());
            ps.setBigDecimal(3, r.getStorageCostUsd());
            ps.setBigDecimal(4, r.getTotalCostUsd());
            ps.setTimestamp(5, java.sql.Timestamp.valueOf(now));
            ps.setString(6,  r.getCalculationNotes());
            ps.setLong(7,    r.getRunId());
        });

        log.info("Marked {} runs as CALCULATED", results.size());
    }

    /**
     * Marks a run FAILED with a diagnostic note.
     * FAILED rows are retried on the next run unless {@code resetFailed=false}.
     */
    public void markFailed(Long runId, String reason) {
        // language=sql
        jdbc.update("""
            UPDATE calculator_run_costs SET
                cost_calculation_status = 'FAILED',
                cost_calculated_at      = ?,
                cost_calculation_notes  = ?,
                updated_at              = NOW()
            WHERE run_id = ?
            """, LocalDateTime.now(), reason, runId);
        log.warn("run_id={} FAILED: {}", runId, reason);
    }

    /**
     * Marks a run SKIPPED and writes explicit zero costs.
     * SKIPPED rows are permanently excluded from future scheduler passes —
     * they represent structural ineligibility (CANCELED, zero-duration, etc.),
     * not transient errors. Re-queue manually if the underlying data is corrected.
     */
    public void markSkipped(Long runId, String reason) {
        // language=sql
        jdbc.update("""
            UPDATE calculator_run_costs SET
                cost_calculation_status = 'SKIPPED',
                cost_calculated_at      = ?,
                cost_calculation_notes  = ?,
                dbu_cost_usd            = 0,
                vm_cost_usd             = 0,
                storage_cost_usd        = 0,
                total_cost_usd          = 0,
                updated_at              = NOW()
            WHERE run_id = ?
            """, LocalDateTime.now(), reason, runId);
        log.info("run_id={} SKIPPED: {}", runId, reason);
    }

    /**
     * Resets CALCULATED and/or FAILED rows back to PENDING for reprocessing.
     *
     * <p>Use cases:
     * <ul>
     *   <li>{@code resetCalculated=true} — after editing a rate constant in
     *       {@code CostCalculationEngine} to retroactively apply the new rate.</li>
     *   <li>{@code resetFailed=true} — after resolving whatever caused failures
     *       (e.g. adding a missing SKU to the rate maps).</li>
     * </ul>
     *
     * <p>SKIPPED rows are intentionally excluded — they require a human decision
     * about whether the underlying data issue has been corrected.
     *
     * @return number of rows reset
     */
    public int resetToPending(boolean resetCalculated, boolean resetFailed) {
        if (!resetCalculated && !resetFailed) return 0;

        String inList = switch (resetCalculated + ":" + resetFailed) {
            case "true:true"  -> "('CALCULATED', 'FAILED')";
            case "true:false" -> "('CALCULATED')";
            default           -> "('FAILED')";
        };

        // language=sql
        int n = jdbc.update("""
            UPDATE calculator_run_costs SET
                cost_calculation_status = 'PENDING',
                cost_calculated_at      = NULL,
                cost_calculation_notes  = NULL,
                dbu_cost_usd            = 0,
                vm_cost_usd             = 0,
                storage_cost_usd        = 0,
                total_cost_usd          = 0,
                updated_at              = NOW()
            WHERE cost_calculation_status IN
            """ + inList);

        log.info("Reset {} rows to PENDING", n);
        return n;
    }

    // =========================================================================
    // Row mapper — only fields the engine actually needs
    // =========================================================================

    private RowMapper<CalculatorRunCost> pendingRowMapper() {
        return (rs, n) -> CalculatorRunCost.builder()
                .id(rs.getLong("cost_id"))
                .runId(rs.getLong("run_id"))
                .calculatorId(rs.getString("calculator_id"))
                .calculatorName(rs.getString("calculator_name"))
                .frequency(RunFrequency.of(rs.getString("frequency")))
                .reportingDate(rs.getObject("reporting_date", java.time.LocalDate.class))
                .startTime(rs.getTimestamp("start_time").toLocalDateTime())
                .endTime(rs.getTimestamp("end_time").toLocalDateTime())
                .durationSeconds(rs.getInt("duration_seconds"))
                .durationHours(rs.getBigDecimal("duration_hours"))
                .status(rs.getString("status"))
                .driverNodeType(rs.getString("driver_node_type"))
                .workerNodeType(rs.getString("worker_node_type"))
                .workerCount((Integer) rs.getObject("worker_count"))
                .spotInstances(rs.getBoolean("spot_instances"))
                .photonEnabled(rs.getBoolean("photon_enabled"))
                .runtimeEngine(rs.getString("runtime_engine"))
                .workloadType(rs.getString("workload_type"))
                .region(rs.getString("region"))
                .autoscaleEnabled(rs.getBoolean("autoscale_enabled"))
                .autoscaleMin((Integer) rs.getObject("autoscale_min"))
                .autoscaleMax((Integer) rs.getObject("autoscale_max"))
                .build();
    }
}