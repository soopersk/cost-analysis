package com.company.observability.repository;

import com.company.observability.dto.*;
import com.company.observability.domain.CalculatorRunCost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    // =========================================================================
    // INSERT / UPDATE OPERATIONS
    // =========================================================================

    /**
     * Insert a new cost record
     */
    public Long insert(CalculatorRunCost cost) {
        String sql = """
            INSERT INTO calculator_run_costs (
                calculator_run_id, reporting_date, calculator_id, calculator_name,
                databricks_run_id, cluster_id, driver_node_type, worker_node_type,
                min_workers, max_workers, avg_worker_count, peak_worker_count,
                spot_instance_used, photon_enabled,
                run_start_time, run_end_time, duration_seconds, run_status,
                is_retry, attempt_number,
                dbu_units_consumed, dbu_unit_price, dbu_cost_usd, dbu_sku,
                vm_driver_cost_usd, vm_worker_cost_usd, vm_total_cost_usd, vm_node_hours,
                storage_input_gb, storage_output_gb, storage_cost_usd,
                network_egress_gb, network_cost_usd, total_cost_usd,
                allocation_method, confidence_score, concurrent_runs_on_cluster,
                cluster_utilization_pct, calculation_version, calculation_timestamp,
                calculated_by, created_at, updated_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            RETURNING cost_id
        """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(sql, new String[]{"cost_id"});
            int idx = 1;
            ps.setString(idx++, cost.getCalculatorRunId());
            ps.setObject(idx++, cost.getReportingDate());
            ps.setString(idx++, cost.getCalculatorId());
            ps.setString(idx++, cost.getCalculatorName());
            ps.setLong(idx++, cost.getDatabricksRunId());
            ps.setString(idx++, cost.getClusterId());
            ps.setString(idx++, cost.getDriverNodeType());
            ps.setString(idx++, cost.getWorkerNodeType());
            ps.setObject(idx++, cost.getMinWorkers());
            ps.setObject(idx++, cost.getMaxWorkers());
            ps.setBigDecimal(idx++, cost.getAvgWorkerCount());
            ps.setObject(idx++, cost.getPeakWorkerCount());
            ps.setBoolean(idx++, cost.getSpotInstanceUsed());
            ps.setBoolean(idx++, cost.getPhotonEnabled());
            ps.setTimestamp(idx++, Timestamp.valueOf(cost.getRunStartTime()));
            ps.setTimestamp(idx++, Timestamp.valueOf(cost.getRunEndTime()));
            ps.setInt(idx++, cost.getDurationSeconds());
            ps.setString(idx++, cost.getRunStatus());
            ps.setBoolean(idx++, cost.getIsRetry());
            ps.setInt(idx++, cost.getAttemptNumber());
            ps.setBigDecimal(idx++, cost.getDbuUnitsConsumed());
            ps.setBigDecimal(idx++, cost.getDbuUnitPrice());
            ps.setBigDecimal(idx++, cost.getDbuCostUsd());
            ps.setString(idx++, cost.getDbuSku());
            ps.setBigDecimal(idx++, cost.getVmDriverCostUsd());
            ps.setBigDecimal(idx++, cost.getVmWorkerCostUsd());
            ps.setBigDecimal(idx++, cost.getVmTotalCostUsd());
            ps.setBigDecimal(idx++, cost.getVmNodeHours());
            ps.setBigDecimal(idx++, cost.getStorageInputGb());
            ps.setBigDecimal(idx++, cost.getStorageOutputGb());
            ps.setBigDecimal(idx++, cost.getStorageCostUsd());
            ps.setBigDecimal(idx++, cost.getNetworkEgressGb());
            ps.setBigDecimal(idx++, cost.getNetworkCostUsd());
            ps.setBigDecimal(idx++, cost.getTotalCostUsd());
            ps.setString(idx++, cost.getAllocationMethod());
            ps.setBigDecimal(idx++, cost.getConfidenceScore());
            ps.setObject(idx++, cost.getConcurrentRunsOnCluster());
            ps.setBigDecimal(idx++, cost.getClusterUtilizationPct());
            ps.setString(idx++, cost.getCalculationVersion());
            ps.setTimestamp(idx++, Timestamp.valueOf(cost.getCalculationTimestamp()));
            ps.setString(idx++, cost.getCalculatedBy());
            ps.setTimestamp(idx++, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(idx++, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);

        return (Long) keyHolder.getKeys().get("cost_id");
    }

    /**
     * Upsert (insert or update) a cost record
     */
    public void upsert(CalculatorRunCost cost) {
        String sql = """
            INSERT INTO calculator_run_costs (
                calculator_run_id, reporting_date, calculator_id, calculator_name,
                databricks_run_id, cluster_id, driver_node_type, worker_node_type,
                avg_worker_count, spot_instance_used, photon_enabled,
                run_start_time, run_end_time, duration_seconds, run_status,
                is_retry, attempt_number,
                dbu_units_consumed, dbu_unit_price, dbu_cost_usd, dbu_sku,
                vm_driver_cost_usd, vm_worker_cost_usd, vm_total_cost_usd,
                storage_cost_usd, total_cost_usd,
                allocation_method, confidence_score,
                calculation_version, calculation_timestamp, calculated_by,
                created_at, updated_at
            ) VALUES (
                :calculatorRunId, :reportingDate, :calculatorId, :calculatorName,
                :databricksRunId, :clusterId, :driverNodeType, :workerNodeType,
                :avgWorkerCount, :spotInstanceUsed, :photonEnabled,
                :runStartTime, :runEndTime, :durationSeconds, :runStatus,
                :isRetry, :attemptNumber,
                :dbuUnitsConsumed, :dbuUnitPrice, :dbuCostUsd, :dbuSku,
                :vmDriverCostUsd, :vmWorkerCostUsd, :vmTotalCostUsd,
                :storageCostUsd, :totalCostUsd,
                :allocationMethod, :confidenceScore,
                :calculationVersion, :calculationTimestamp, :calculatedBy,
                NOW(), NOW()
            )
            ON CONFLICT (calculator_run_id, reporting_date)
            DO UPDATE SET
                dbu_cost_usd = EXCLUDED.dbu_cost_usd,
                vm_total_cost_usd = EXCLUDED.vm_total_cost_usd,
                storage_cost_usd = EXCLUDED.storage_cost_usd,
                total_cost_usd = EXCLUDED.total_cost_usd,
                confidence_score = EXCLUDED.confidence_score,
                calculation_timestamp = EXCLUDED.calculation_timestamp,
                updated_at = NOW()
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("calculatorRunId", cost.getCalculatorRunId())
                .addValue("reportingDate", cost.getReportingDate())
                .addValue("calculatorId", cost.getCalculatorId())
                .addValue("calculatorName", cost.getCalculatorName())
                .addValue("databricksRunId", cost.getDatabricksRunId())
                .addValue("clusterId", cost.getClusterId())
                .addValue("driverNodeType", cost.getDriverNodeType())
                .addValue("workerNodeType", cost.getWorkerNodeType())
                .addValue("avgWorkerCount", cost.getAvgWorkerCount())
                .addValue("spotInstanceUsed", cost.getSpotInstanceUsed())
                .addValue("photonEnabled", cost.getPhotonEnabled())
                .addValue("runStartTime", cost.getRunStartTime())
                .addValue("runEndTime", cost.getRunEndTime())
                .addValue("durationSeconds", cost.getDurationSeconds())
                .addValue("runStatus", cost.getRunStatus())
                .addValue("isRetry", cost.getIsRetry())
                .addValue("attemptNumber", cost.getAttemptNumber())
                .addValue("dbuUnitsConsumed", cost.getDbuUnitsConsumed())
                .addValue("dbuUnitPrice", cost.getDbuUnitPrice())
                .addValue("dbuCostUsd", cost.getDbuCostUsd())
                .addValue("dbuSku", cost.getDbuSku())
                .addValue("vmDriverCostUsd", cost.getVmDriverCostUsd())
                .addValue("vmWorkerCostUsd", cost.getVmWorkerCostUsd())
                .addValue("vmTotalCostUsd", cost.getVmTotalCostUsd())
                .addValue("storageCostUsd", cost.getStorageCostUsd())
                .addValue("totalCostUsd", cost.getTotalCostUsd())
                .addValue("allocationMethod", cost.getAllocationMethod())
                .addValue("confidenceScore", cost.getConfidenceScore())
                .addValue("calculationVersion", cost.getCalculationVersion())
                .addValue("calculationTimestamp", cost.getCalculationTimestamp())
                .addValue("calculatedBy", cost.getCalculatedBy());

        namedParameterJdbcTemplate.update(sql, params);
    }

    /**
     * Batch upsert for multiple cost records (more efficient)
     */
    public void batchUpsert(List<CalculatorRunCost> costs) {
        String sql = """
            INSERT INTO calculator_run_costs (
                calculator_run_id, reporting_date, calculator_id, calculator_name,
                databricks_run_id, cluster_id, driver_node_type, worker_node_type,
                avg_worker_count, spot_instance_used, photon_enabled,
                run_start_time, run_end_time, duration_seconds, run_status,
                is_retry, attempt_number,
                dbu_cost_usd, vm_total_cost_usd, storage_cost_usd, total_cost_usd,
                allocation_method, confidence_score,
                calculation_version, calculation_timestamp,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (calculator_run_id, reporting_date)
            DO UPDATE SET
                total_cost_usd = EXCLUDED.total_cost_usd,
                dbu_cost_usd = EXCLUDED.dbu_cost_usd,
                vm_total_cost_usd = EXCLUDED.vm_total_cost_usd,
                updated_at = NOW()
        """;

        jdbcTemplate.batchUpdate(sql, costs, costs.size(), (ps, cost) -> {
            int idx = 1;
            ps.setString(idx++, cost.getCalculatorRunId());
            ps.setObject(idx++, cost.getReportingDate());
            ps.setString(idx++, cost.getCalculatorId());
            ps.setString(idx++, cost.getCalculatorName());
            ps.setLong(idx++, cost.getDatabricksRunId());
            ps.setString(idx++, cost.getClusterId());
            ps.setString(idx++, cost.getDriverNodeType());
            ps.setString(idx++, cost.getWorkerNodeType());
            ps.setBigDecimal(idx++, cost.getAvgWorkerCount());
            ps.setBoolean(idx++, cost.getSpotInstanceUsed());
            ps.setBoolean(idx++, cost.getPhotonEnabled());
            ps.setTimestamp(idx++, Timestamp.valueOf(cost.getRunStartTime()));
            ps.setTimestamp(idx++, Timestamp.valueOf(cost.getRunEndTime()));
            ps.setInt(idx++, cost.getDurationSeconds());
            ps.setString(idx++, cost.getRunStatus());
            ps.setBoolean(idx++, cost.getIsRetry());
            ps.setInt(idx++, cost.getAttemptNumber());
            ps.setBigDecimal(idx++, cost.getDbuCostUsd());
            ps.setBigDecimal(idx++, cost.getVmTotalCostUsd());
            ps.setBigDecimal(idx++, cost.getStorageCostUsd());
            ps.setBigDecimal(idx++, cost.getTotalCostUsd());
            ps.setString(idx++, cost.getAllocationMethod());
            ps.setBigDecimal(idx++, cost.getConfidenceScore());
            ps.setString(idx++, cost.getCalculationVersion());
            ps.setTimestamp(idx++, Timestamp.valueOf(cost.getCalculationTimestamp()));
        });

        log.info("Batch upserted {} cost records", costs.size());
    }

    // =========================================================================
    // QUERY OPERATIONS
    // =========================================================================

    /**
     * Get daily cost trends aggregated across all calculators
     */
    public List<DailyCostTrend> getDailyCostTrendsAllCalculators(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT 
                reporting_date,
                SUM(total_cost_usd) as total_cost,
                SUM(dbu_cost_usd) as dbu_cost,
                SUM(vm_total_cost_usd) as vm_cost,
                SUM(storage_cost_usd) as storage_cost,
                SUM(COALESCE(network_cost_usd, 0)) as network_cost
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
            GROUP BY reporting_date
            ORDER BY reporting_date
        """;

        return jdbcTemplate.query(sql, dailyCostTrendRowMapper(), startDate, endDate);
    }

    /**
     * Get daily cost trends for a specific calculator
     */
    public List<DailyCostTrend> getDailyCostTrendsByCalculator(
            LocalDate startDate,
            LocalDate endDate,
            String calculatorId
    ) {
        String sql = """
            SELECT 
                reporting_date,
                SUM(total_cost_usd) as total_cost,
                SUM(dbu_cost_usd) as dbu_cost,
                SUM(vm_total_cost_usd) as vm_cost,
                SUM(storage_cost_usd) as storage_cost,
                SUM(COALESCE(network_cost_usd, 0)) as network_cost
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
              AND calculator_id = ?
            GROUP BY reporting_date
            ORDER BY reporting_date
        """;

        return jdbcTemplate.query(sql, dailyCostTrendRowMapper(), startDate, endDate, calculatorId);
    }

    /**
     * Get calculator-level cost summary
     */
    public List<CalculatorCostSummary> getCalculatorCostSummary(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT 
                calculator_id,
                calculator_name,
                COUNT(*) as total_runs,
                SUM(total_cost_usd) as total_cost,
                AVG(total_cost_usd) as avg_cost_per_run,
                SUM(dbu_cost_usd) as dbu_cost,
                SUM(vm_total_cost_usd) as vm_cost,
                SUM(storage_cost_usd) as storage_cost,
                AVG(confidence_score) as avg_confidence_score,
                COUNT(*) FILTER (WHERE run_status = 'SUCCESS') as successful_runs,
                COUNT(*) FILTER (WHERE run_status = 'FAILED') as failed_runs,
                COUNT(*) FILTER (WHERE is_retry = true) as retry_runs,
                COUNT(*) FILTER (WHERE spot_instance_used = true) as spot_instance_runs,
                COUNT(*) FILTER (WHERE photon_enabled = true) as photon_enabled_runs
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
            GROUP BY calculator_id, calculator_name
            ORDER BY SUM(total_cost_usd) DESC
        """;

        return jdbcTemplate.query(sql, calculatorCostSummaryRowMapper(), startDate, endDate);
    }

    /**
     * Get recent runs with cost details
     */
    public List<RecentRunCost> getRecentRunsWithCost(int limit, String calculatorId, String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                calculator_run_id,
                calculator_id,
                calculator_name,
                run_start_time,
                run_end_time,
                duration_seconds,
                run_status,
                dbu_cost_usd,
                vm_total_cost_usd,
                storage_cost_usd,
                total_cost_usd,
                avg_worker_count,
                spot_instance_used,
                photon_enabled,
                is_retry,
                attempt_number
            FROM calculator_run_costs
            WHERE 1=1
        """);

        Map<String, Object> params = new HashMap<>();

        if (calculatorId != null) {
            sql.append(" AND calculator_id = :calculatorId");
            params.put("calculatorId", calculatorId);
        }

        if (status != null) {
            sql.append(" AND run_status = :status");
            params.put("status", status);
        }

        sql.append(" ORDER BY run_start_time DESC LIMIT :limit");
        params.put("limit", limit);

        return namedParameterJdbcTemplate.query(sql.toString(), params, recentRunCostRowMapper());
    }

    /**
     * Get cost breakdown by component
     */
    public CostBreakdown getCostBreakdown(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT 
                SUM(dbu_cost_usd) as dbu_cost,
                SUM(vm_total_cost_usd) as vm_cost,
                SUM(storage_cost_usd) as storage_cost,
                SUM(COALESCE(network_cost_usd, 0)) as network_cost,
                SUM(total_cost_usd) as total_cost
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
        """;

        return jdbcTemplate.queryForObject(sql, costBreakdownRowMapper(), startDate, endDate);
    }

    /**
     * Get efficiency metrics
     */
    public EfficiencyMetrics getEfficiencyMetrics(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT 
                AVG(CASE WHEN spot_instance_used = true THEN 100.0 ELSE 0.0 END) as spot_instance_usage,
                AVG(CASE WHEN photon_enabled = true THEN 100.0 ELSE 0.0 END) as photon_adoption,
                AVG(COALESCE(cluster_utilization_pct, 0)) as avg_cluster_utilization,
                AVG(CASE WHEN is_retry = true THEN 100.0 ELSE 0.0 END) as retry_rate,
                AVG(CASE WHEN run_status = 'FAILED' THEN 100.0 ELSE 0.0 END) as failure_rate
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
        """;

        return jdbcTemplate.queryForObject(sql, efficiencyMetricsRowMapper(), startDate, endDate);
    }

    /**
     * Get cost anomalies (runs that cost more than threshold × average)
     */
    public List<CostAnomaly> getCostAnomalies(double threshold, LocalDate startDate, LocalDate endDate) {
        String sql = """
            WITH calc_avg AS (
                SELECT 
                    calculator_id,
                    AVG(total_cost_usd) as avg_cost,
                    STDDEV(total_cost_usd) as std_dev
                FROM calculator_run_costs
                WHERE reporting_date BETWEEN ? AND ?
                  AND run_status = 'SUCCESS'
                GROUP BY calculator_id
            )
            SELECT 
                c.calculator_run_id,
                c.calculator_id,
                c.calculator_name,
                c.run_start_time,
                c.total_cost_usd as actual_cost,
                ca.avg_cost as average_cost,
                (c.total_cost_usd / ca.avg_cost) as cost_multiplier,
                c.run_status,
                c.duration_seconds
            FROM calculator_run_costs c
            INNER JOIN calc_avg ca ON c.calculator_id = ca.calculator_id
            WHERE c.reporting_date BETWEEN ? AND ?
              AND c.total_cost_usd > (ca.avg_cost * ?)
            ORDER BY (c.total_cost_usd / ca.avg_cost) DESC
        """;

        return jdbcTemplate.query(sql, costAnomalyRowMapper(),
                startDate, endDate, startDate, endDate, threshold);
    }

    /**
     * Get all runs for CSV export
     */
    public List<RecentRunCost> getAllRunsForExport(LocalDate startDate, LocalDate endDate, String calculatorId) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                calculator_run_id,
                calculator_id,
                calculator_name,
                run_start_time,
                run_end_time,
                duration_seconds,
                run_status,
                dbu_cost_usd,
                vm_total_cost_usd,
                storage_cost_usd,
                total_cost_usd,
                avg_worker_count,
                spot_instance_used,
                photon_enabled,
                is_retry,
                attempt_number
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN :startDate AND :endDate
        """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        if (calculatorId != null) {
            sql.append(" AND calculator_id = :calculatorId");
            params.addValue("calculatorId", calculatorId);
        }

        sql.append(" ORDER BY run_start_time DESC");

        return namedParameterJdbcTemplate.query(sql.toString(), params, recentRunCostRowMapper());
    }

    // =========================================================================
    // ROW MAPPERS
    // =========================================================================

    private RowMapper<DailyCostTrend> dailyCostTrendRowMapper() {
        return (rs, rowNum) -> new DailyCostTrend(
                rs.getObject("reporting_date", LocalDate.class),
                rs.getBigDecimal("total_cost"),
                rs.getBigDecimal("dbu_cost"),
                rs.getBigDecimal("vm_cost"),
                rs.getBigDecimal("storage_cost"),
                rs.getBigDecimal("network_cost")
        );
    }

    private RowMapper<CalculatorCostSummary> calculatorCostSummaryRowMapper() {
        return (rs, rowNum) -> new CalculatorCostSummary(
                rs.getString("calculator_id"),
                rs.getString("calculator_name"),
                rs.getLong("total_runs"),
                rs.getBigDecimal("total_cost"),
                rs.getBigDecimal("avg_cost_per_run"),
                rs.getBigDecimal("dbu_cost"),
                rs.getBigDecimal("vm_cost"),
                rs.getBigDecimal("storage_cost"),
                rs.getBigDecimal("avg_confidence_score"),
                rs.getLong("successful_runs"),
                rs.getLong("failed_runs"),
                rs.getLong("retry_runs"),
                rs.getLong("spot_instance_runs"),
                rs.getLong("photon_enabled_runs")
        );
    }

    private RowMapper<RecentRunCost> recentRunCostRowMapper() {
        return (rs, rowNum) -> new RecentRunCost(
                rs.getString("calculator_run_id"),
                rs.getString("calculator_id"),
                rs.getString("calculator_name"),
                rs.getTimestamp("run_start_time").toLocalDateTime(),
                rs.getTimestamp("run_end_time").toLocalDateTime(),
                rs.getInt("duration_seconds"),
                rs.getString("run_status"),
                rs.getBigDecimal("dbu_cost_usd"),
                rs.getBigDecimal("vm_total_cost_usd"),
                rs.getBigDecimal("storage_cost_usd"),
                rs.getBigDecimal("total_cost_usd"),
                rs.getBigDecimal("avg_worker_count"),
                rs.getBoolean("spot_instance_used"),
                rs.getBoolean("photon_enabled"),
                rs.getBoolean("is_retry"),
                rs.getInt("attempt_number")
        );
    }

    private RowMapper<CostBreakdown> costBreakdownRowMapper() {
        return (rs, rowNum) -> new CostBreakdown(
                rs.getBigDecimal("dbu_cost"),
                rs.getBigDecimal("vm_cost"),
                rs.getBigDecimal("storage_cost"),
                rs.getBigDecimal("network_cost"),
                rs.getBigDecimal("total_cost")
        );
    }

    private RowMapper<EfficiencyMetrics> efficiencyMetricsRowMapper() {
        return (rs, rowNum) -> new EfficiencyMetrics(
                rs.getDouble("spot_instance_usage"),
                rs.getDouble("photon_adoption"),
                rs.getDouble("avg_cluster_utilization"),
                rs.getDouble("retry_rate"),
                rs.getDouble("failure_rate")
        );
    }

    private RowMapper<CostAnomaly> costAnomalyRowMapper() {
        return (rs, rowNum) -> new CostAnomaly(
                rs.getString("calculator_run_id"),
                rs.getString("calculator_id"),
                rs.getString("calculator_name"),
                rs.getTimestamp("run_start_time").toLocalDateTime(),
                rs.getBigDecimal("actual_cost"),
                rs.getBigDecimal("average_cost"),
                rs.getBigDecimal("cost_multiplier"),
                rs.getString("run_status"),
                rs.getInt("duration_seconds")
        );
    }

    private RowMapper<CalculatorRunCost> calculatorRunCostRowMapper() {
        return (rs, rowNum) -> {
            CalculatorRunCost cost = new CalculatorRunCost();
            cost.setCostId(rs.getLong("cost_id"));
            cost.setCalculatorRunId(rs.getString("calculator_run_id"));
            cost.setReportingDate(rs.getObject("reporting_date", LocalDate.class));
            cost.setCalculatorId(rs.getString("calculator_id"));
            cost.setCalculatorName(rs.getString("calculator_name"));
            cost.setDatabricksRunId(rs.getLong("databricks_run_id"));
            cost.setClusterId(rs.getString("cluster_id"));
            cost.setDriverNodeType(rs.getString("driver_node_type"));
            cost.setWorkerNodeType(rs.getString("worker_node_type"));
            cost.setAvgWorkerCount(rs.getBigDecimal("avg_worker_count"));
            cost.setSpotInstanceUsed(rs.getBoolean("spot_instance_used"));
            cost.setPhotonEnabled(rs.getBoolean("photon_enabled"));
            cost.setRunStartTime(rs.getTimestamp("run_start_time").toLocalDateTime());
            cost.setRunEndTime(rs.getTimestamp("run_end_time").toLocalDateTime());
            cost.setDurationSeconds(rs.getInt("duration_seconds"));
            cost.setRunStatus(rs.getString("run_status"));
            cost.setDbuCostUsd(rs.getBigDecimal("dbu_cost_usd"));
            cost.setVmTotalCostUsd(rs.getBigDecimal("vm_total_cost_usd"));
            cost.setStorageCostUsd(rs.getBigDecimal("storage_cost_usd"));
            cost.setTotalCostUsd(rs.getBigDecimal("total_cost_usd"));
            cost.setAllocationMethod(rs.getString("allocation_method"));
            cost.setConfidenceScore(rs.getBigDecimal("confidence_score"));
            return cost;
        };
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Find cost record by run ID
     */
    public Optional<CalculatorRunCost> findByRunId(String runId, LocalDate reportingDate) {
        String sql = """
            SELECT * FROM calculator_run_costs
            WHERE calculator_run_id = ? AND reporting_date = ?
        """;

        List<CalculatorRunCost> results = jdbcTemplate.query(
                sql,
                calculatorRunCostRowMapper(),
                runId,
                reportingDate
        );

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Delete cost records older than retention period
     */
    public int deleteOlderThan(LocalDate cutoffDate) {
        String sql = "DELETE FROM calculator_run_costs WHERE reporting_date < ?";
        return jdbcTemplate.update(sql, cutoffDate);
    }

    /**
     * Get total cost for a date range
     */
    public BigDecimal getTotalCost(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT COALESCE(SUM(total_cost_usd), 0) 
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
        """;

        return jdbcTemplate.queryForObject(sql, BigDecimal.class, startDate, endDate);
    }

    /**
     * Count runs for a specific calculator
     */
    public long countRunsByCalculator(String calculatorId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT COUNT(*) FROM calculator_run_costs
            WHERE calculator_id = ? AND reporting_date BETWEEN ? AND ?
        """;

        return jdbcTemplate.queryForObject(sql, Long.class, calculatorId, startDate, endDate);
    }
}