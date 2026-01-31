package com.company.observability.repository;

import com.company.observability.dto.*;
import com.company.observability.entity.CalculatorRunCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CalculatorRunCostRepository extends JpaRepository<CalculatorRunCost, Long> {

    /**
     * Get daily cost trends aggregated across all calculators
     */
    @Query("""
        SELECT new com.company.observability.dto.DailyCostTrend(
            c.reportingDate,
            SUM(c.totalCostUsd),
            SUM(c.dbuCostUsd),
            SUM(c.vmTotalCostUsd),
            SUM(c.storageCostUsd),
            SUM(COALESCE(c.networkCostUsd, 0))
        )
        FROM CalculatorRunCost c
        WHERE c.reportingDate BETWEEN :startDate AND :endDate
        GROUP BY c.reportingDate
        ORDER BY c.reportingDate
    """)
    List<DailyCostTrend> getDailyCostTrendsAllCalculators(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Get daily cost trends for a specific calculator
     */
    @Query("""
        SELECT new com.company.observability.dto.DailyCostTrend(
            c.reportingDate,
            SUM(c.totalCostUsd),
            SUM(c.dbuCostUsd),
            SUM(c.vmTotalCostUsd),
            SUM(c.storageCostUsd),
            SUM(COALESCE(c.networkCostUsd, 0))
        )
        FROM CalculatorRunCost c
        WHERE c.reportingDate BETWEEN :startDate AND :endDate
          AND c.calculatorId = :calculatorId
        GROUP BY c.reportingDate
        ORDER BY c.reportingDate
    """)
    List<DailyCostTrend> getDailyCostTrendsByCalculator(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("calculatorId") String calculatorId
    );

    /**
     * Get calculator-level cost summary
     */
    @Query("""
        SELECT new com.company.observability.dto.CalculatorCostSummary(
            c.calculatorId,
            c.calculatorName,
            COUNT(c),
            SUM(c.totalCostUsd),
            AVG(c.totalCostUsd),
            SUM(c.dbuCostUsd),
            SUM(c.vmTotalCostUsd),
            SUM(c.storageCostUsd),
            AVG(c.confidenceScore),
            SUM(CASE WHEN c.runStatus = 'SUCCESS' THEN 1 ELSE 0 END),
            SUM(CASE WHEN c.runStatus = 'FAILED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN c.isRetry = true THEN 1 ELSE 0 END),
            SUM(CASE WHEN c.spotInstanceUsed = true THEN 1 ELSE 0 END),
            SUM(CASE WHEN c.photonEnabled = true THEN 1 ELSE 0 END)
        )
        FROM CalculatorRunCost c
        WHERE c.reportingDate BETWEEN :startDate AND :endDate
        GROUP BY c.calculatorId, c.calculatorName
        ORDER BY SUM(c.totalCostUsd) DESC
    """)
    List<CalculatorCostSummary> getCalculatorCostSummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Get recent runs with cost details
     */
    @Query("""
        SELECT new com.company.observability.dto.RecentRunCost(
            c.calculatorRunId,
            c.calculatorId,
            c.calculatorName,
            c.runStartTime,
            c.runEndTime,
            c.durationSeconds,
            c.runStatus,
            c.dbuCostUsd,
            c.vmTotalCostUsd,
            c.storageCostUsd,
            c.totalCostUsd,
            c.avgWorkerCount,
            c.spotInstanceUsed,
            c.photonEnabled,
            c.isRetry,
            c.attemptNumber
        )
        FROM CalculatorRunCost c
        WHERE (:calculatorId IS NULL OR c.calculatorId = :calculatorId)
          AND (:status IS NULL OR c.runStatus = :status)
        ORDER BY c.runStartTime DESC
        LIMIT :limit
    """)
    List<RecentRunCost> getRecentRunsWithCost(
            @Param("limit") int limit,
            @Param("calculatorId") String calculatorId,
            @Param("status") String status
    );

    /**
     * Get cost breakdown by component
     */
    @Query("""
        SELECT new com.company.observability.dto.CostBreakdown(
            SUM(c.dbuCostUsd),
            SUM(c.vmTotalCostUsd),
            SUM(c.storageCostUsd),
            SUM(COALESCE(c.networkCostUsd, 0)),
            SUM(c.totalCostUsd)
        )
        FROM CalculatorRunCost c
        WHERE c.reportingDate BETWEEN :startDate AND :endDate
    """)
    CostBreakdown getCostBreakdown(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Get efficiency metrics
     */
    @Query("""
        SELECT new com.company.observability.dto.EfficiencyMetrics(
            AVG(CASE WHEN c.spotInstanceUsed = true THEN 100.0 ELSE 0.0 END),
            AVG(CASE WHEN c.photonEnabled = true THEN 100.0 ELSE 0.0 END),
            AVG(COALESCE(c.clusterUtilizationPct, 0)),
            AVG(CASE WHEN c.isRetry = true THEN 100.0 ELSE 0.0 END),
            AVG(CASE WHEN c.runStatus = 'FAILED' THEN 100.0 ELSE 0.0 END)
        )
        FROM CalculatorRunCost c
        WHERE c.reportingDate BETWEEN :startDate AND :endDate
    """)
    EfficiencyMetrics getEfficiencyMetrics(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Get cost anomalies (runs that cost more than threshold × average)
     */
    @Query("""
        WITH calc_avg AS (
            SELECT 
                c.calculatorId,
                AVG(c.totalCostUsd) as avgCost,
                STDDEV(c.totalCostUsd) as stdDev
            FROM CalculatorRunCost c
            WHERE c.reportingDate BETWEEN :startDate AND :endDate
              AND c.runStatus = 'SUCCESS'
            GROUP BY c.calculatorId
        )
        SELECT new com.company.observability.dto.CostAnomaly(
            c.calculatorRunId,
            c.calculatorId,
            c.calculatorName,
            c.runStartTime,
            c.totalCostUsd,
            ca.avgCost,
            (c.totalCostUsd / ca.avgCost),
            c.runStatus,
            c.durationSeconds
        )
        FROM CalculatorRunCost c
        JOIN calc_avg ca ON c.calculatorId = ca.calculatorId
        WHERE c.reportingDate BETWEEN :startDate AND :endDate
          AND c.totalCostUsd > (ca.avgCost * :threshold)
        ORDER BY (c.totalCostUsd / ca.avgCost) DESC
    """)
    List<CostAnomaly> getCostAnomalies(
            @Param("threshold") double threshold,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Get all runs for CSV export
     */
    @Query("""
        SELECT new com.company.observability.dto.RecentRunCost(
            c.calculatorRunId,
            c.calculatorId,
            c.calculatorName,
            c.runStartTime,
            c.runEndTime,
            c.durationSeconds,
            c.runStatus,
            c.dbuCostUsd,
            c.vmTotalCostUsd,
            c.storageCostUsd,
            c.totalCostUsd,
            c.avgWorkerCount,
            c.spotInstanceUsed,
            c.photonEnabled,
            c.isRetry,
            c.attemptNumber
        )
        FROM CalculatorRunCost c
        WHERE c.reportingDate BETWEEN :startDate AND :endDate
          AND (:calculatorId IS NULL OR c.calculatorId = :calculatorId)
        ORDER BY c.runStartTime DESC
    """)
    List<RecentRunCost> getAllRunsForExport(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("calculatorId") String calculatorId
    );
}
