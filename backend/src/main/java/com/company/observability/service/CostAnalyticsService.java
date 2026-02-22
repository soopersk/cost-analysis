package com.company.observability.service;

import com.company.observability.domain.CalculatorRunCost;
import com.company.observability.domain.RunFrequency;
import com.company.observability.dto.*;
import com.company.observability.repository.CalculatorRunCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cost analytics service.
 *
 * <p>Every public method accepts {@link RunFrequency} as a first-class
 * parameter. Callers must always specify whether they want DAILY or MONTHLY
 * cost analysis — there is no default that silently mixes both cadences.
 */
@Service
@RequiredArgsConstructor
public class CostAnalyticsService {

    private final CalculatorRunCostRepository costRepository;

    // -------------------------------------------------------------------------
    // Dashboard
    // -------------------------------------------------------------------------

    /**
     * Full dashboard payload for a given frequency and period.
     *
     * @param frequency   DAILY or MONTHLY — determines which runs are included
     * @param period      "7d", "30d", or "90d" relative to today's reporting date
     * @param calculatorId  specific calculator, or null / "all" for all
     */
    public CostDashboardResponse getDashboardData(
            RunFrequency frequency, String period, String calculatorId) {

        LocalDate endDate   = LocalDate.now();
        LocalDate startDate = resolveStartDate(period, endDate);
        String    calcFilter = "all".equals(calculatorId) ? null : calculatorId;

        return CostDashboardResponse.builder()
                .frequency(frequency)
                .dailyCosts(getCostTrend(frequency, startDate, endDate, calcFilter))
                .calculatorCosts(getCalculatorCostSummary(frequency, startDate, endDate))
                .recentRuns(getRecentRuns(frequency, 20, calcFilter, null))
                .costBreakdown(getCostBreakdown(frequency, startDate, endDate))
                .efficiency(getEfficiencyMetrics(frequency, startDate, endDate))
                .period(period)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    // -------------------------------------------------------------------------
    // Cost trend (grouped by reporting_date)
    // -------------------------------------------------------------------------

    public List<DailyCostTrend> getCostTrend(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate, String calculatorId) {
        return calculatorId == null
                ? costRepository.getCostTrendAllCalculators(frequency, startDate, endDate)
                : costRepository.getCostTrendByCalculator(frequency, startDate, endDate, calculatorId);
    }

    // -------------------------------------------------------------------------
    // Month-over-month trend per (calculator, frequency)
    // -------------------------------------------------------------------------

    /**
     * Month-over-month cost trend for one (calculator, frequency) pair.
     * DAILY and MONTHLY trends for the same calculator are always separate series.
     */
    public List<MonthlyCostTrend> getMonthlyCostTrend(
            String calculatorId, RunFrequency frequency, int months) {
        return costRepository.getMonthlyCostTrend(calculatorId, frequency, months);
    }

    // -------------------------------------------------------------------------
    // Per-(calculator, frequency) summary
    // -------------------------------------------------------------------------

    /**
     * Returns one summary row per (calculator, frequency) pair.
     * If {@code frequency} is null, all frequencies are returned together
     * (useful for a cross-frequency admin view).
     */
    public List<CalculatorCostSummary> getCalculatorCostSummary(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {
        return costRepository.getCalculatorCostSummary(frequency, startDate, endDate);
    }

    public List<CalculatorCostSummary> getTopExpensiveCalculators(
            RunFrequency frequency, int limit, LocalDate startDate, LocalDate endDate) {
        return costRepository.getCalculatorCostSummary(frequency, startDate, endDate)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Recent runs
    // -------------------------------------------------------------------------

    public List<RecentRunCost> getRecentRuns(
            RunFrequency frequency, int limit, String calculatorId, String status) {
        return costRepository.getRecentRunsWithCost(limit, calculatorId, frequency, status);
    }

    // -------------------------------------------------------------------------
    // Cost breakdown
    // -------------------------------------------------------------------------

    public CostBreakdown getCostBreakdown(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {
        return costRepository.getCostBreakdown(frequency, startDate, endDate);
    }

    // -------------------------------------------------------------------------
    // Efficiency metrics
    // -------------------------------------------------------------------------

    public EfficiencyMetrics getEfficiencyMetrics(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {
        return costRepository.getEfficiencyMetrics(frequency, startDate, endDate);
    }

    // -------------------------------------------------------------------------
    // Anomaly detection
    // -------------------------------------------------------------------------

    /**
     * Anomalies are always scoped to the same (calculator, frequency) pair —
     * a MONTHLY run is never benchmarked against DAILY run averages.
     */
    public List<CostAnomaly> getCostAnomalies(
            RunFrequency frequency, double threshold,
            LocalDate startDate, LocalDate endDate) {
        return costRepository.getCostAnomalies(frequency, threshold, startDate, endDate);
    }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    public byte[] exportToCsv(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate, String calculatorId) {

        List<CalculatorRunCost> runs =
                costRepository.getAllRunsForExport(frequency, startDate, endDate, calculatorId);

        StringBuilder csv = new StringBuilder();
        csv.append("run_id,calculator_id,calculator_name,frequency,reporting_date,job_name,")
                .append("start_time,end_time,duration_seconds,status,")
                .append("dbu_cost_usd,vm_cost_usd,storage_cost_usd,total_cost_usd,")
                .append("worker_count,spot_instances,photon_enabled,tenant_abb\n");

        for (CalculatorRunCost r : runs) {
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%d,%s,%.4f,%.4f,%.4f,%.4f,%s,%s,%s,%s\n",
                    r.getRunId(),
                    r.getCalculatorId(),
                    r.getCalculatorName(),
                    r.getFrequency().name(),
                    r.getReportingDate(),
                    r.getJobName(),
                    r.getStartTime(),
                    r.getEndTime(),
                    r.getDurationSeconds(),
                    r.getStatus(),
                    orZero(r.getDbuCostUsd()),
                    orZero(r.getVmCostUsd()),
                    orZero(r.getStorageCostUsd()),
                    orZero(r.getTotalCostUsd()),
                    r.getWorkerCount(),
                    r.getSpotInstances(),
                    r.getPhotonEnabled(),
                    r.getTenantAbb()
            ));
        }

        return csv.toString().getBytes();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LocalDate resolveStartDate(String period, LocalDate endDate) {
        if (period != null && period.matches("\\d+d")) {
            int days = Integer.parseInt(period.substring(0, period.length() - 1));
            return endDate.minusDays(days);
        }
        return endDate.minusDays(30);
    }

    private BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}