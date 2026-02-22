package com.company.observability.repository;

import com.company.observability.domain.CalculatorRunCost;
import com.company.observability.domain.RunFrequency;
import com.company.observability.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class CalculatorRunCostRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    // =========================================================================
    // WRITE
    // =========================================================================

    /**
     * Idempotent batch upsert.
     *
     * <p>ON CONFLICT (run_id): mutable fields (status, costs, end timing) are
     * updated; immutable identifiers and scheduling dimensions (frequency,
     * reporting_date) are left unchanged to protect historical records.
     */
    public void batchUpsert(List<CalculatorRunCost> runs) {
        if (runs.isEmpty()) return;

        // language=sql
        String sql = """
            INSERT INTO calculator_run_costs (
                run_id, job_id, job_name,
                calculator_id, calculator_name,
                frequency, reporting_date,
                cluster_id,
                start_time, end_time, duration_seconds, duration_hours,
                status,
                driver_node_type, worker_node_type, worker_count,
                spot_instances, photon_enabled,
                spark_version, runtime_engine, cluster_source, workload_type, region,
                autoscale_enabled, autoscale_min, autoscale_max,
                task_version, context_id, megdp_run_id, tenant_abb,
                dbu_cost_usd, vm_cost_usd, storage_cost_usd, total_cost_usd
            ) VALUES (
                :runId, :jobId, :jobName,
                :calculatorId, :calculatorName,
                :frequency, :reportingDate,
                :clusterId,
                :startTime, :endTime, :durationSeconds, :durationHours,
                :status,
                :driverNodeType, :workerNodeType, :workerCount,
                :spotInstances, :photonEnabled,
                :sparkVersion, :runtimeEngine, :clusterSource, :workloadType, :region,
                :autoscaleEnabled, :autoscaleMin, :autoscaleMax,
                :taskVersion, :contextId, :megdpRunId, :tenantAbb,
                :dbuCostUsd, :vmCostUsd, :storageCostUsd, :totalCostUsd
            )
            ON CONFLICT (run_id, reporting_date) DO UPDATE SET
                status                  = EXCLUDED.status,
                end_time                = EXCLUDED.end_time,
                duration_seconds        = EXCLUDED.duration_seconds,
                duration_hours          = EXCLUDED.duration_hours,
                cost_calculation_status = 'PENDING',
                cost_calculated_at      = NULL,
                cost_calculation_notes  = NULL,
                updated_at              = NOW()
            """;

        namedJdbc.batchUpdate(
            sql,
            runs.stream()
                .map(run -> new MapSqlParameterSource()
                    .addValue("runId", run.getRunId())
                    .addValue("jobId", run.getJobId())
                    .addValue("jobName", run.getJobName())
                    .addValue("calculatorId", run.getCalculatorId())
                    .addValue("calculatorName", run.getCalculatorName())
                    .addValue("frequency", run.getFrequency().name())
                    .addValue("reportingDate", run.getReportingDate())
                    .addValue("clusterId", run.getClusterId())
                    .addValue("startTime", run.getStartTime())
                    .addValue("endTime", run.getEndTime())
                    .addValue("durationSeconds", run.getDurationSeconds())
                    .addValue("durationHours", run.getDurationHours())
                    .addValue("status", run.getStatus())
                    .addValue("driverNodeType", run.getDriverNodeType())
                    .addValue("workerNodeType", run.getWorkerNodeType())
                    .addValue("workerCount", run.getWorkerCount())
                    .addValue("spotInstances", run.getSpotInstances())
                    .addValue("photonEnabled", run.getPhotonEnabled())
                    .addValue("sparkVersion", run.getSparkVersion())
                    .addValue("runtimeEngine", run.getRuntimeEngine())
                    .addValue("clusterSource", run.getClusterSource())
                    .addValue("workloadType", run.getWorkloadType())
                    .addValue("region", run.getRegion())
                    .addValue("autoscaleEnabled", run.getAutoscaleEnabled())
                    .addValue("autoscaleMin", run.getAutoscaleMin())
                    .addValue("autoscaleMax", run.getAutoscaleMax())
                    .addValue("taskVersion", run.getTaskVersion())
                    .addValue("contextId", run.getContextId())
                    .addValue("megdpRunId", run.getMegdpRunId())
                    .addValue("tenantAbb", run.getTenantAbb())
                    .addValue("dbuCostUsd", run.getDbuCostUsd())
                    .addValue("vmCostUsd", run.getVmCostUsd())
                    .addValue("storageCostUsd", run.getStorageCostUsd())
                    .addValue("totalCostUsd", run.getTotalCostUsd())
                )
                .toArray(MapSqlParameterSource[]::new)
        );

        log.info("Batch upserted {} calculator run cost records", runs.size());
    }

    // =========================================================================
    // ANALYTICS — all queries are frequency-scoped
    // =========================================================================

    /**
     * Cost totals per reporting_date for all calculators within a frequency.
     *
     * <p>{@code frequency} is mandatory — DAILY and MONTHLY trends must never
     * be mixed on the same chart axis.
     */
    public List<DailyCostTrend> getCostTrendAllCalculators(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {

        // language=sql
        String sql = """
            SELECT
                reporting_date                AS date,
                SUM(total_cost_usd)           AS total_cost,
                SUM(dbu_cost_usd)             AS dbu_cost,
                SUM(vm_cost_usd)              AS vm_cost,
                SUM(storage_cost_usd)         AS storage_cost
            FROM calculator_run_costs
            WHERE frequency       = ?
              AND reporting_date BETWEEN ? AND ?
            GROUP BY reporting_date
            ORDER BY reporting_date
            """;

        return jdbc.query(sql, dailyCostTrendRowMapper(), frequency.name(), startDate, endDate);
    }

    /**
     * Cost totals per reporting_date for a single (calculator, frequency) pair.
     */
    public List<DailyCostTrend> getCostTrendByCalculator(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate, String calculatorId) {

        // language=sql
        String sql = """
            SELECT
                reporting_date                AS date,
                SUM(total_cost_usd)           AS total_cost,
                SUM(dbu_cost_usd)             AS dbu_cost,
                SUM(vm_cost_usd)              AS vm_cost,
                SUM(storage_cost_usd)         AS storage_cost
            FROM calculator_run_costs
            WHERE frequency       = ?
              AND calculator_id   = ?
              AND reporting_date BETWEEN ? AND ?
            GROUP BY reporting_date
            ORDER BY reporting_date
            """;

        return jdbc.query(sql, dailyCostTrendRowMapper(),
                frequency.name(), calculatorId, startDate, endDate);
    }

    /**
     * Month-over-month cost trend for one (calculator, frequency) pair.
     *
     * <p>Groups by the calendar month of {@code reporting_date}, not
     * {@code start_time}. This keeps late-running jobs in the correct
     * reporting period. Returns one row per calendar month, oldest first.
     */
    public List<MonthlyCostTrend> getMonthlyCostTrend(
            String calculatorId, RunFrequency frequency, int months) {

        // Fetch an extra 12 months beyond the requested window so that
        // LAG(total_cost, 12) can produce non-null YoY values for the
        // first month in the caller's window.  The outer WHERE then trims
        // the result back to the requested period.
        // language=sql
        String sql = """
            WITH monthly_agg AS (
                SELECT
                    DATE_TRUNC('month', reporting_date)              AS period,
                    TO_CHAR(DATE_TRUNC('month', reporting_date), 'YYYY-MM') AS month,
                    calculator_id,
                    calculator_name,
                    frequency,
                    COUNT(*)            AS total_runs,
                    SUM(total_cost_usd) AS total_cost,
                    AVG(total_cost_usd) AS avg_cost_per_run,
                    MIN(total_cost_usd) AS min_cost,
                    MAX(total_cost_usd) AS max_cost
                FROM calculator_run_costs
                WHERE calculator_id   = ?
                  AND frequency       = ?
                  AND reporting_date >= (CURRENT_DATE - ((? + 12) * INTERVAL '1 month'))
                GROUP BY 1, 2, 3, 4, 5
            ),
            with_deltas AS (
                SELECT *,
                    total_cost - LAG(total_cost)      OVER w  AS mom_delta,
                    ROUND(
                        100.0 * (total_cost - LAG(total_cost)      OVER w)
                        / NULLIF(LAG(total_cost)      OVER w, 0), 2) AS mom_pct,
                    total_cost - LAG(total_cost, 12)  OVER w  AS yoy_delta,
                    ROUND(
                        100.0 * (total_cost - LAG(total_cost, 12)  OVER w)
                        / NULLIF(LAG(total_cost, 12)  OVER w, 0), 2) AS yoy_pct
                FROM monthly_agg
                WINDOW w AS (PARTITION BY calculator_id, frequency ORDER BY period)
            )
            SELECT *
            FROM with_deltas
            WHERE period >= (CURRENT_DATE - (? * INTERVAL '1 month'))
            ORDER BY period
            """;

        return jdbc.query(sql, monthlyCostTrendRowMapper(),
                calculatorId, frequency.name(), months, months);
    }

    /**
     * Per-(calculator, frequency) cost summary for the given reporting date range.
     *
     * <p>A calculator running at both cadences produces two rows — one for
     * DAILY, one for MONTHLY. The result is sorted by total cost descending
     * within each frequency, then by frequency (DAILY first).
     *
     * <p>Pass {@code frequency = null} to return all frequencies together
     * (useful for an admin overview). Pass a specific frequency to get only
     * that cadence's results.
     */
    public List<CalculatorCostSummary> getCalculatorCostSummary(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {

        // language=sql
        StringBuilder sql = new StringBuilder("""
            SELECT
                calculator_id,
                calculator_name,
                frequency,
                COUNT(*)                                                    AS total_runs,
                SUM(total_cost_usd)                                         AS total_cost,
                AVG(total_cost_usd)                                         AS avg_cost_per_run,
                SUM(dbu_cost_usd)                                           AS dbu_cost,
                SUM(vm_cost_usd)                                            AS vm_cost,
                SUM(storage_cost_usd)                                       AS storage_cost,
                COUNT(*) FILTER (WHERE status = 'SUCCESS')                  AS successful_runs,
                COUNT(*) FILTER (WHERE status = 'FAILED')                   AS failed_runs,
                COUNT(*) FILTER (WHERE spot_instances = TRUE)               AS spot_instance_runs,
                COUNT(*) FILTER (WHERE photon_enabled  = TRUE)              AS photon_enabled_runs
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
            """);

        if (frequency != null) {
            sql.append(" AND frequency = '").append(frequency.name()).append("'");
        }

        sql.append("""
            GROUP BY calculator_id, calculator_name, frequency
            ORDER BY frequency, SUM(total_cost_usd) DESC
            """);

        return jdbc.query(sql.toString(), calculatorCostSummaryRowMapper(), startDate, endDate);
    }

    /**
     * Recent runs with full cost detail.
     * Both {@code calculatorId} and {@code frequency} are optional filters.
     */
    public List<RecentRunCost> getRecentRunsWithCost(
            int limit, String calculatorId, RunFrequency frequency, String status) {

        // language=sql
        StringBuilder sql = new StringBuilder("""
            SELECT
                run_id, calculator_id, calculator_name,
                frequency, reporting_date,
                start_time, end_time, duration_seconds,
                status,
                dbu_cost_usd, vm_cost_usd, storage_cost_usd, total_cost_usd,
                worker_count, spot_instances, photon_enabled, tenant_abb
            FROM calculator_run_costs
            WHERE 1=1
            """);

        Map<String, Object> params = new HashMap<>();

        if (calculatorId != null) {
            sql.append(" AND calculator_id = :calculatorId");
            params.put("calculatorId", calculatorId);
        }
        if (frequency != null) {
            sql.append(" AND frequency = :frequency");
            params.put("frequency", frequency.name());
        }
        if (status != null) {
            sql.append(" AND status = :status");
            params.put("status", status);
        }

        sql.append(" ORDER BY reporting_date DESC, start_time DESC LIMIT :limit");
        params.put("limit", limit);

        return namedJdbc.query(sql.toString(), params, recentRunCostRowMapper());
    }

    /**
     * Cost component breakdown scoped to a frequency and reporting date range.
     */
    public CostBreakdown getCostBreakdown(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {

        // language=sql
        String sql = """
            SELECT
                SUM(dbu_cost_usd)       AS dbu_cost,
                SUM(vm_cost_usd)        AS vm_cost,
                SUM(storage_cost_usd)   AS storage_cost,
                SUM(total_cost_usd)     AS total_cost
            FROM calculator_run_costs
            WHERE frequency      = ?
              AND reporting_date BETWEEN ? AND ?
            """;

        return jdbc.queryForObject(sql, costBreakdownRowMapper(), frequency.name(), startDate, endDate);
    }

    /**
     * Efficiency metrics scoped to a frequency and reporting date range.
     */
    public EfficiencyMetrics getEfficiencyMetrics(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {

        // language=sql
        String sql = """
            SELECT
                AVG(CASE WHEN spot_instances = TRUE  THEN 100.0 ELSE 0.0 END) AS spot_instance_usage_pct,
                AVG(CASE WHEN photon_enabled  = TRUE  THEN 100.0 ELSE 0.0 END) AS photon_adoption_pct,
                AVG(CASE WHEN status = 'FAILED'       THEN 100.0 ELSE 0.0 END) AS failure_rate_pct
            FROM calculator_run_costs
            WHERE frequency      = ?
              AND reporting_date BETWEEN ? AND ?
            """;

        return jdbc.queryForObject(sql, efficiencyMetricsRowMapper(), frequency.name(), startDate, endDate);
    }

    /**
     * Anomaly detection: runs whose cost exceeds {@code threshold} ×
     * the per-(calculator, frequency) average.
     *
     * <p>The CTE average is computed within the same frequency only —
     * a MONTHLY run is never benchmarked against DAILY run averages.
     */
    public List<CostAnomaly> getCostAnomalies(
            RunFrequency frequency, double threshold,
            LocalDate startDate, LocalDate endDate) {

        // language=sql
        String sql = """
            WITH calc_avg AS (
                SELECT
                    calculator_id,
                    frequency,
                    AVG(total_cost_usd) AS avg_cost
                FROM calculator_run_costs
                WHERE frequency      = ?
                  AND reporting_date BETWEEN ? AND ?
                  AND status = 'SUCCESS'
                GROUP BY calculator_id, frequency
                HAVING COUNT(*) > 1
            )
            SELECT
                c.run_id,
                c.calculator_id,
                c.calculator_name,
                c.frequency,
                c.reporting_date,
                c.start_time,
                c.total_cost_usd               AS actual_cost,
                ca.avg_cost                    AS average_cost,
                c.total_cost_usd / ca.avg_cost AS cost_multiplier,
                c.status,
                c.duration_seconds
            FROM calculator_run_costs c
            JOIN calc_avg ca
              ON  c.calculator_id = ca.calculator_id
              AND c.frequency     = ca.frequency
            WHERE c.frequency      = ?
              AND c.reporting_date BETWEEN ? AND ?
              AND c.total_cost_usd > (ca.avg_cost * ?)
            ORDER BY c.total_cost_usd / ca.avg_cost DESC
            """;

        return jdbc.query(sql, costAnomalyRowMapper(),
                frequency.name(), startDate, endDate,   // CTE params
                frequency.name(), startDate, endDate,   // outer params
                threshold);
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    public Optional<CalculatorRunCost> findByRunId(Long runId) {
        String sql = "SELECT * FROM calculator_run_costs WHERE run_id = ?";
        List<CalculatorRunCost> rows = jdbc.query(sql, fullRowMapper(), runId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public BigDecimal getTotalCost(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {

        String sql = """
            SELECT COALESCE(SUM(total_cost_usd), 0)
            FROM calculator_run_costs
            WHERE frequency      = ?
              AND reporting_date BETWEEN ? AND ?
            """;
        return jdbc.queryForObject(sql, BigDecimal.class, frequency.name(), startDate, endDate);
    }

    public List<CalculatorRunCost> getAllRunsForExport(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate, String calculatorId) {

        StringBuilder sql = new StringBuilder("""
            SELECT * FROM calculator_run_costs
            WHERE frequency      = :frequency
              AND reporting_date BETWEEN :startDate AND :endDate
            """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("frequency",  frequency.name())
                .addValue("startDate",  startDate)
                .addValue("endDate",    endDate);

        if (calculatorId != null) {
            sql.append(" AND calculator_id = :calculatorId");
            params.addValue("calculatorId", calculatorId);
        }

        sql.append(" ORDER BY reporting_date DESC, start_time DESC");
        return namedJdbc.query(sql.toString(), params, fullRowMapper());
    }

    public int deleteOlderThan(RunFrequency frequency, LocalDate cutoffDate) {
        return jdbc.update(
                "DELETE FROM calculator_run_costs WHERE frequency = ? AND reporting_date < ?",
                frequency.name(), cutoffDate);
    }

    // =========================================================================
    // ROW MAPPERS
    // =========================================================================

    private RowMapper<DailyCostTrend> dailyCostTrendRowMapper() {
        return (rs, n) -> new DailyCostTrend(
                rs.getObject("date", LocalDate.class),
                rs.getBigDecimal("total_cost"),
                rs.getBigDecimal("dbu_cost"),
                rs.getBigDecimal("vm_cost"),
                rs.getBigDecimal("storage_cost")
        );
    }

    private RowMapper<MonthlyCostTrend> monthlyCostTrendRowMapper() {
        return (rs, n) -> new MonthlyCostTrend(
                rs.getString("month"),
                rs.getString("calculator_id"),
                rs.getString("calculator_name"),
                RunFrequency.of(rs.getString("frequency")),
                rs.getLong("total_runs"),
                rs.getBigDecimal("total_cost"),
                rs.getBigDecimal("avg_cost_per_run"),
                rs.getBigDecimal("min_cost"),
                rs.getBigDecimal("max_cost"),
                rs.getBigDecimal("mom_delta"),
                rs.getBigDecimal("mom_pct"),
                rs.getBigDecimal("yoy_delta"),
                rs.getBigDecimal("yoy_pct")
        );
    }

    private RowMapper<CalculatorCostSummary> calculatorCostSummaryRowMapper() {
        return (rs, n) -> new CalculatorCostSummary(
                rs.getString("calculator_id"),
                rs.getString("calculator_name"),
                RunFrequency.of(rs.getString("frequency")),
                rs.getLong("total_runs"),
                rs.getBigDecimal("total_cost"),
                rs.getBigDecimal("avg_cost_per_run"),
                rs.getBigDecimal("dbu_cost"),
                rs.getBigDecimal("vm_cost"),
                rs.getBigDecimal("storage_cost"),
                rs.getLong("successful_runs"),
                rs.getLong("failed_runs"),
                rs.getLong("spot_instance_runs"),
                rs.getLong("photon_enabled_runs")
        );
    }

    private RowMapper<RecentRunCost> recentRunCostRowMapper() {
        return (rs, n) -> new RecentRunCost(
                rs.getLong("run_id"),
                rs.getString("calculator_id"),
                rs.getString("calculator_name"),
                RunFrequency.of(rs.getString("frequency")),
                rs.getObject("reporting_date", LocalDate.class),
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getInt("duration_seconds"),
                rs.getString("status"),
                rs.getBigDecimal("dbu_cost_usd"),
                rs.getBigDecimal("vm_cost_usd"),
                rs.getBigDecimal("storage_cost_usd"),
                rs.getBigDecimal("total_cost_usd"),
                (Integer) rs.getObject("worker_count"),
                rs.getBoolean("spot_instances"),
                rs.getBoolean("photon_enabled"),
                rs.getString("tenant_abb")
        );
    }

    private RowMapper<CostBreakdown> costBreakdownRowMapper() {
        return (rs, n) -> new CostBreakdown(
                rs.getBigDecimal("dbu_cost"),
                rs.getBigDecimal("vm_cost"),
                rs.getBigDecimal("storage_cost"),
                rs.getBigDecimal("total_cost")
        );
    }

    private RowMapper<EfficiencyMetrics> efficiencyMetricsRowMapper() {
        return (rs, n) -> new EfficiencyMetrics(
                rs.getDouble("spot_instance_usage_pct"),
                rs.getDouble("photon_adoption_pct"),
                rs.getDouble("failure_rate_pct")
        );
    }

    private RowMapper<CostAnomaly> costAnomalyRowMapper() {
        return (rs, n) -> new CostAnomaly(
                rs.getLong("run_id"),
                rs.getString("calculator_id"),
                rs.getString("calculator_name"),
                RunFrequency.of(rs.getString("frequency")),
                rs.getObject("reporting_date", LocalDate.class),
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getBigDecimal("actual_cost"),
                rs.getBigDecimal("average_cost"),
                rs.getBigDecimal("cost_multiplier"),
                rs.getString("status"),
                rs.getInt("duration_seconds")
        );
    }

    private RowMapper<CalculatorRunCost> fullRowMapper() {
        return (rs, n) -> CalculatorRunCost.builder()
                .id(rs.getLong("cost_id"))
                .runId(rs.getLong("run_id"))
                .jobId(rs.getLong("job_id"))
                .jobName(rs.getString("job_name"))
                .calculatorId(rs.getString("calculator_id"))
                .calculatorName(rs.getString("calculator_name"))
                .frequency(RunFrequency.of(rs.getString("frequency")))
                .reportingDate(rs.getObject("reporting_date", LocalDate.class))
                .clusterId(rs.getString("cluster_id"))
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
                .sparkVersion(rs.getString("spark_version"))
                .runtimeEngine(rs.getString("runtime_engine"))
                .clusterSource(rs.getString("cluster_source"))
                .workloadType(rs.getString("workload_type"))
                .region(rs.getString("region"))
                .autoscaleEnabled(rs.getBoolean("autoscale_enabled"))
                .autoscaleMin((Integer) rs.getObject("autoscale_min"))
                .autoscaleMax((Integer) rs.getObject("autoscale_max"))
                .taskVersion(rs.getString("task_version"))
                .contextId(rs.getString("context_id"))
                .megdpRunId(rs.getString("megdp_run_id"))
                .tenantAbb(rs.getString("tenant_abb"))
                .dbuCostUsd(rs.getBigDecimal("dbu_cost_usd"))
                .vmCostUsd(rs.getBigDecimal("vm_cost_usd"))
                .storageCostUsd(rs.getBigDecimal("storage_cost_usd"))
                .totalCostUsd(rs.getBigDecimal("total_cost_usd"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static Timestamp toTs(LocalDateTime ldt) {
        return ldt == null ? null : Timestamp.valueOf(ldt);
    }

    private static boolean bool(Boolean b) {
        return Boolean.TRUE.equals(b);
    }
}
