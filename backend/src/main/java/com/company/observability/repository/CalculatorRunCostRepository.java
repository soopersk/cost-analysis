package com.company.observability.repository;

import com.company.observability.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CalculatorRunCostRepository {

    private final JdbcTemplate jdbcTemplate;

    /* ---------------------------------------------------
       DAILY COST TRENDS
    --------------------------------------------------- */

    public List<DailyCostTrend> getDailyCostTrendsAllCalculators(
            LocalDate startDate, LocalDate endDate) {

        String sql = """
            SELECT reporting_date,
                   SUM(total_cost_usd),
                   SUM(dbu_cost_usd),
                   SUM(vm_total_cost_usd),
                   SUM(storage_cost_usd),
                   SUM(COALESCE(network_cost_usd, 0))
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
            GROUP BY reporting_date
            ORDER BY reporting_date
        """;

        return jdbcTemplate.query(sql,
                (rs, i) -> new DailyCostTrend(
                        rs.getDate(1).toLocalDate(),
                        rs.getBigDecimal(2),
                        rs.getBigDecimal(3),
                        rs.getBigDecimal(4),
                        rs.getBigDecimal(5),
                        rs.getBigDecimal(6)
                ),
                startDate, endDate
        );
    }

    public List<DailyCostTrend> getDailyCostTrendsByCalculator(
            LocalDate startDate, LocalDate endDate, String calculatorId) {

        String sql = """
            SELECT reporting_date,
                   SUM(total_cost_usd),
                   SUM(dbu_cost_usd),
                   SUM(vm_total_cost_usd),
                   SUM(storage_cost_usd),
                   SUM(COALESCE(network_cost_usd, 0))
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
              AND calculator_id = ?
            GROUP BY reporting_date
            ORDER BY reporting_date
        """;

        return jdbcTemplate.query(sql,
                (rs, i) -> new DailyCostTrend(
                        rs.getDate(1).toLocalDate(),
                        rs.getBigDecimal(2),
                        rs.getBigDecimal(3),
                        rs.getBigDecimal(4),
                        rs.getBigDecimal(5),
                        rs.getBigDecimal(6)
                ),
                startDate, endDate, calculatorId
        );
    }

    /* ---------------------------------------------------
       CALCULATOR SUMMARY
    --------------------------------------------------- */

    public List<CalculatorCostSummary> getCalculatorCostSummary(
            LocalDate startDate, LocalDate endDate) {

        String sql = """
            SELECT calculator_id,
                   calculator_name,
                   COUNT(*),
                   SUM(total_cost_usd),
                   AVG(total_cost_usd),
                   SUM(dbu_cost_usd),
                   SUM(vm_total_cost_usd),
                   SUM(storage_cost_usd),
                   AVG(confidence_score),
                   SUM(CASE WHEN run_status = 'SUCCESS' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN run_status = 'FAILED' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN is_retry THEN 1 ELSE 0 END),
                   SUM(CASE WHEN spot_instance_used THEN 1 ELSE 0 END),
                   SUM(CASE WHEN photon_enabled THEN 1 ELSE 0 END)
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
            GROUP BY calculator_id, calculator_name
            ORDER BY SUM(total_cost_usd) DESC
        """;

        return jdbcTemplate.query(sql,
                (rs, i) -> new CalculatorCostSummary(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getLong(3),
                        rs.getBigDecimal(4),
                        rs.getBigDecimal(5),
                        rs.getBigDecimal(6),
                        rs.getBigDecimal(7),
                        rs.getBigDecimal(8),
                        rs.getBigDecimal(9),
                        rs.getLong(10),
                        rs.getLong(11),
                        rs.getLong(12),
                        rs.getLong(13),
                        rs.getLong(14)
                ),
                startDate, endDate
        );
    }

    /* ---------------------------------------------------
       RECENT RUNS
    --------------------------------------------------- */

    public List<RecentRunCost> getRecentRunsWithCost(
            int limit, String calculatorId, String status) {

        String sql = """
            SELECT calculator_run_id, calculator_id, calculator_name,
                   run_start_time, run_end_time, duration_seconds,
                   run_status, dbu_cost_usd, vm_total_cost_usd,
                   storage_cost_usd, total_cost_usd,
                   avg_worker_count, spot_instance_used,
                   photon_enabled, is_retry, attempt_number
            FROM calculator_run_costs
            WHERE (? IS NULL OR calculator_id = ?)
              AND (? IS NULL OR run_status = ?)
            ORDER BY run_start_time DESC
            LIMIT ?
        """;

        return jdbcTemplate.query(sql,
                (rs, i) -> new RecentRunCost(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getTimestamp(4).toLocalDateTime(),
                        rs.getTimestamp(5).toLocalDateTime(),
                        rs.getInt(6),
                        rs.getString(7),
                        rs.getBigDecimal(8),
                        rs.getBigDecimal(9),
                        rs.getBigDecimal(10),
                        rs.getBigDecimal(11),
                        rs.getBigDecimal(12),
                        rs.getBoolean(13),
                        rs.getBoolean(14),
                        rs.getBoolean(15),
                        rs.getInt(16)
                ),
                calculatorId, calculatorId,
                status, status,
                limit
        );
    }

    /* ---------------------------------------------------
       COST BREAKDOWN
    --------------------------------------------------- */

    public CostBreakdown getCostBreakdown(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT SUM(dbu_cost_usd),
                   SUM(vm_total_cost_usd),
                   SUM(storage_cost_usd),
                   SUM(COALESCE(network_cost_usd, 0)),
                   SUM(total_cost_usd)
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
        """;

        return jdbcTemplate.queryForObject(sql,
                (rs, i) -> new CostBreakdown(
                        rs.getBigDecimal(1),
                        rs.getBigDecimal(2),
                        rs.getBigDecimal(3),
                        rs.getBigDecimal(4),
                        rs.getBigDecimal(5)
                ),
                startDate, endDate
        );
    }

    /* ---------------------------------------------------
       EFFICIENCY METRICS
    --------------------------------------------------- */

    public EfficiencyMetrics getEfficiencyMetrics(
            LocalDate startDate, LocalDate endDate) {

        String sql = """
            SELECT
              AVG(CASE WHEN spot_instance_used THEN 100 ELSE 0 END),
              AVG(CASE WHEN photon_enabled THEN 100 ELSE 0 END),
              AVG(COALESCE(cluster_utilization_pct, 0)),
              AVG(CASE WHEN is_retry THEN 100 ELSE 0 END),
              AVG(CASE WHEN run_status = 'FAILED' THEN 100 ELSE 0 END)
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
        """;

        return jdbcTemplate.queryForObject(sql,
                (rs, i) -> new EfficiencyMetrics(
                        rs.getDouble(1),
                        rs.getDouble(2),
                        rs.getDouble(3),
                        rs.getDouble(4),
                        rs.getDouble(5)
                ),
                startDate, endDate
        );
    }

    /* ---------------------------------------------------
       COST ANOMALIES
    --------------------------------------------------- */

    public List<CostAnomaly> getCostAnomalies(
            double threshold, LocalDate startDate, LocalDate endDate) {

        String sql = """
            WITH avg_cost AS (
                SELECT calculator_id, AVG(total_cost_usd) avg_cost
                FROM calculator_run_costs
                WHERE reporting_date BETWEEN ? AND ?
                  AND run_status = 'SUCCESS'
                GROUP BY calculator_id
            )
            SELECT c.calculator_run_id, c.calculator_id, c.calculator_name,
                   c.run_start_time, c.total_cost_usd,
                   a.avg_cost,
                   (c.total_cost_usd / a.avg_cost),
                   c.run_status, c.duration_seconds
            FROM calculator_run_costs c
            JOIN avg_cost a ON c.calculator_id = a.calculator_id
            WHERE c.total_cost_usd > (a.avg_cost * ?)
            ORDER BY (c.total_cost_usd / a.avg_cost) DESC
        """;

        return jdbcTemplate.query(sql,
                (rs, i) -> new CostAnomaly(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getTimestamp(4).toLocalDateTime(),
                        rs.getBigDecimal(5),
                        rs.getBigDecimal(6),
                        rs.getBigDecimal(7),
                        rs.getString(8),
                        rs.getInt(9)
                ),
                startDate, endDate, threshold
        );
    }

    public List<RecentRunCost> getAllRunsForExport(
            LocalDate startDate, LocalDate endDate, String calculatorId) {

        String sql = """
            SELECT calculator_run_id, calculator_id, calculator_name,
                   run_start_time, run_end_time, duration_seconds,
                   run_status, dbu_cost_usd, vm_total_cost_usd,
                   storage_cost_usd, total_cost_usd,
                   avg_worker_count, spot_instance_used,
                   photon_enabled, is_retry, attempt_number
            FROM calculator_run_costs
            WHERE reporting_date BETWEEN ? AND ?
              AND (? IS NULL OR calculator_id = ?)
            ORDER BY run_start_time DESC
        """;

        return jdbcTemplate.query(sql,
                (rs, i) -> new RecentRunCost(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getTimestamp(4).toLocalDateTime(),
                        rs.getTimestamp(5).toLocalDateTime(),
                        rs.getInt(6),
                        rs.getString(7),
                        rs.getBigDecimal(8),
                        rs.getBigDecimal(9),
                        rs.getBigDecimal(10),
                        rs.getBigDecimal(11),
                        rs.getBigDecimal(12),
                        rs.getBoolean(13),
                        rs.getBoolean(14),
                        rs.getBoolean(15),
                        rs.getInt(16)
                ),
                startDate, endDate,
                calculatorId, calculatorId
        );
    }
}
