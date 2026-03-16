package com.company.observability.service;

import com.company.observability.config.CostAnalyticsProperties;
import com.company.observability.domain.CalculatorRunCost;
import com.company.observability.domain.RunFrequency;
import com.company.observability.dto.*;
import com.company.observability.repository.CalculatorRunCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CostAnalyticsService {

    private final CalculatorRunCostRepository costRepository;
    private final CostAnalyticsProperties     properties;

    // -------------------------------------------------------------------------
    // Dashboard
    // -------------------------------------------------------------------------

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
    // Cost trend (grouped by reporting_date) — NOT filtered (date-aggregated totals)
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

    public List<MonthlyCostTrend> getMonthlyCostTrend(
            String calculatorId, RunFrequency frequency, int months) {
        return costRepository.getMonthlyCostTrend(calculatorId, frequency, months);
    }

    // -------------------------------------------------------------------------
    // Per-(calculator, frequency) summary — FILTERED
    // -------------------------------------------------------------------------

    public List<CalculatorCostSummary> getCalculatorCostSummary(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {
        return costRepository.getCalculatorCostSummary(frequency, startDate, endDate)
                .stream()
                .filter(s -> !isExcluded(s.getCalculatorId()))
                .toList();
    }

    public List<CalculatorCostSummary> getTopExpensiveCalculators(
            RunFrequency frequency, int limit, LocalDate startDate, LocalDate endDate) {
        return costRepository.getCalculatorCostSummary(frequency, startDate, endDate)
                .stream()
                .filter(s -> !isExcluded(s.getCalculatorId()))
                .limit(limit)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Recent runs — FILTERED
    // -------------------------------------------------------------------------

    public List<RecentRunCost> getRecentRuns(
            RunFrequency frequency, int limit, String calculatorId, String status) {
        return costRepository.getRecentRunsWithCost(limit, calculatorId, frequency, status)
                .stream()
                .filter(r -> !isExcluded(r.getCalculatorId()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Cost breakdown — NOT filtered (aggregate totals, no calculatorId field)
    // -------------------------------------------------------------------------

    public CostBreakdown getCostBreakdown(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {
        return costRepository.getCostBreakdown(frequency, startDate, endDate);
    }

    // -------------------------------------------------------------------------
    // Efficiency metrics — NOT filtered (aggregate totals, no calculatorId field)
    // -------------------------------------------------------------------------

    public EfficiencyMetrics getEfficiencyMetrics(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate) {
        return costRepository.getEfficiencyMetrics(frequency, startDate, endDate);
    }

    // -------------------------------------------------------------------------
    // Anomaly detection — FILTERED
    // -------------------------------------------------------------------------

    public List<CostAnomaly> getCostAnomalies(
            RunFrequency frequency, double threshold,
            LocalDate startDate, LocalDate endDate) {
        return costRepository.getCostAnomalies(frequency, threshold, startDate, endDate)
                .stream()
                .filter(a -> !isExcluded(a.getCalculatorId()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Export — FILTERED
    // -------------------------------------------------------------------------

    public byte[] exportToCsv(
            RunFrequency frequency, LocalDate startDate, LocalDate endDate, String calculatorId) {

        List<CalculatorRunCost> runs =
                costRepository.getAllRunsForExport(frequency, startDate, endDate, calculatorId)
                        .stream()
                        .filter(r -> !isExcluded(r.getCalculatorId()))
                        .toList();

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

    /**
     * Returns true if the given calculatorId matches any pattern in the
     * excluded-calculators list. Patterns support a single '*' wildcard:
     * <ul>
     *   <li>{@code *suffix}  — matches any id ending with "suffix"</li>
     *   <li>{@code prefix*}  — matches any id starting with "prefix"</li>
     *   <li>{@code *middle*} — matches any id containing "middle"</li>
     *   <li>{@code exact}    — exact match (no wildcard)</li>
     * </ul>
     * All comparisons are case-insensitive.
     */
    private boolean isExcluded(String calculatorId) {
        if (calculatorId == null) return false;
        String id = calculatorId.toLowerCase();
        for (String pattern : properties.getExcludedCalculators()) {
            String p = pattern.toLowerCase();
            if (!p.contains("*")) {
                if (id.equals(p)) return true;
            } else if (p.startsWith("*") && p.endsWith("*")) {
                String middle = p.substring(1, p.length() - 1);
                if (id.contains(middle)) return true;
            } else if (p.startsWith("*")) {
                if (id.endsWith(p.substring(1))) return true;
            } else if (p.endsWith("*")) {
                if (id.startsWith(p.substring(0, p.length() - 1))) return true;
            }
        }
        return false;
    }

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
