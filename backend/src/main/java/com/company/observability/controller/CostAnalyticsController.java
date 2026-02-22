package com.company.observability.controller;

import com.company.observability.domain.RunFrequency;
import com.company.observability.dto.*;
import com.company.observability.service.CostAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Cost analytics REST API.
 *
 * <h3>Frequency parameter</h3>
 * Every endpoint that returns aggregated data requires {@code ?frequency=DAILY}
 * or {@code ?frequency=MONTHLY}. This is intentional — the API refuses to mix
 * cost cadences silently. A calculator running at both frequencies has two
 * completely independent cost profiles.
 *
 * <h3>URL structure</h3>
 * <pre>
 *   GET /api/v1/cost-analytics?frequency=DAILY&period=30d
 *   GET /api/v1/cost-analytics/cost-trend?frequency=DAILY&startDate=...&endDate=...
 *   GET /api/v1/cost-analytics/monthly-trend?frequency=MONTHLY&calculatorId=...&months=12
 *   GET /api/v1/cost-analytics/by-calculator?frequency=DAILY&startDate=...&endDate=...
 *   GET /api/v1/cost-analytics/recent-runs?frequency=DAILY
 *   GET /api/v1/cost-analytics/breakdown?frequency=DAILY&startDate=...&endDate=...
 *   GET /api/v1/cost-analytics/efficiency?frequency=DAILY&startDate=...&endDate=...
 *   GET /api/v1/cost-analytics/top-expensive?frequency=MONTHLY&startDate=...&endDate=...
 *   GET /api/v1/cost-analytics/anomalies?frequency=DAILY&startDate=...&endDate=...
 *   GET /api/v1/cost-analytics/export/csv?frequency=DAILY&startDate=...&endDate=...
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/cost-analytics")
@RequiredArgsConstructor
public class CostAnalyticsController {

    private final CostAnalyticsService costAnalyticsService;

    /**
     * Full dashboard payload.
     * Requires frequency — the dashboard is always rendered for one cadence at a time.
     */
    @GetMapping
    public ResponseEntity<CostDashboardResponse> getDashboardData(
            @RequestParam(defaultValue = "DAILY")  RunFrequency frequency,
            @RequestParam(defaultValue = "30d")    String       period,
            @RequestParam(defaultValue = "all")    String       calculator
    ) {
        return ResponseEntity.ok(
                costAnalyticsService.getDashboardData(frequency, period, calculator));
    }

    /**
     * Cost totals per reporting_date, scoped to a frequency.
     * For DAILY calculators this gives a day-by-day series.
     * For MONTHLY calculators this gives one data point per month.
     */
    @GetMapping("/cost-trend")
    public ResponseEntity<List<DailyCostTrend>> getCostTrend(
            @RequestParam                                                RunFrequency frequency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    endDate,
            @RequestParam(required = false)                              String       calculatorId
    ) {
        return ResponseEntity.ok(
                costAnalyticsService.getCostTrend(frequency, startDate, endDate, calculatorId));
    }

    /**
     * Month-over-month cost trend for one (calculator, frequency) pair.
     * Typically used with frequency=MONTHLY for monthly calculators and
     * frequency=DAILY to show daily-calculator cost by calendar month.
     */
    @GetMapping("/monthly-trend")
    public ResponseEntity<List<MonthlyCostTrend>> getMonthlyCostTrend(
            @RequestParam              RunFrequency frequency,
            @RequestParam              String       calculatorId,
            @RequestParam(defaultValue = "12") int  months
    ) {
        return ResponseEntity.ok(
                costAnalyticsService.getMonthlyCostTrend(calculatorId, frequency, months));
    }

    /**
     * Per-(calculator, frequency) cost summary.
     * A calculator running at both cadences appears as two separate rows.
     */
    @GetMapping("/by-calculator")
    public ResponseEntity<List<CalculatorCostSummary>> getCalculatorCosts(
            @RequestParam                                                RunFrequency frequency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    endDate
    ) {
        return ResponseEntity.ok(
                costAnalyticsService.getCalculatorCostSummary(frequency, startDate, endDate));
    }

    /**
     * Recent runs, ordered by reporting_date DESC then start_time DESC.
     * Both calculatorId and status are optional secondary filters.
     */
    @GetMapping("/recent-runs")
    public ResponseEntity<List<RecentRunCost>> getRecentRuns(
            @RequestParam                          RunFrequency frequency,
            @RequestParam(defaultValue = "20")     int          limit,
            @RequestParam(required = false)        String       calculatorId,
            @RequestParam(required = false)        String       status
    ) {
        return ResponseEntity.ok(
                costAnalyticsService.getRecentRuns(frequency, limit, calculatorId, status));
    }

    /** DBU / VM / Storage breakdown for a frequency and date range. */
    @GetMapping("/breakdown")
    public ResponseEntity<CostBreakdown> getCostBreakdown(
            @RequestParam                                                RunFrequency frequency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    endDate
    ) {
        return ResponseEntity.ok(
                costAnalyticsService.getCostBreakdown(frequency, startDate, endDate));
    }

    /** Spot usage, Photon adoption, and failure rate for a frequency and date range. */
    @GetMapping("/efficiency")
    public ResponseEntity<EfficiencyMetrics> getEfficiencyMetrics(
            @RequestParam                                                RunFrequency frequency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    endDate
    ) {
        return ResponseEntity.ok(
                costAnalyticsService.getEfficiencyMetrics(frequency, startDate, endDate));
    }

    /** Top N most expensive calculators within a frequency. */
    @GetMapping("/top-expensive")
    public ResponseEntity<List<CalculatorCostSummary>> getTopExpensive(
            @RequestParam                                                RunFrequency frequency,
            @RequestParam(defaultValue = "10")                           int          limit,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    endDate
    ) {
        return ResponseEntity.ok(
                costAnalyticsService.getTopExpensiveCalculators(frequency, limit, startDate, endDate));
    }

    /**
     * Runs whose cost exceeds {@code threshold} × the per-(calculator, frequency) average.
     * Anomaly detection is always within the same frequency.
     */
    @GetMapping("/anomalies")
    public ResponseEntity<List<CostAnomaly>> getCostAnomalies(
            @RequestParam                                                RunFrequency frequency,
            @RequestParam(defaultValue = "3.0")                          double       threshold,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    endDate
    ) {
        return ResponseEntity.ok(
                costAnalyticsService.getCostAnomalies(frequency, threshold, startDate, endDate));
    }

    /** Export runs to CSV for a given frequency and date range. */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportToCsv(
            @RequestParam                                                RunFrequency frequency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate    endDate,
            @RequestParam(required = false)                              String       calculatorId
    ) {
        byte[] csv = costAnalyticsService.exportToCsv(frequency, startDate, endDate, calculatorId);
        String filename = String.format("calculator-costs-%s-%s-to-%s.csv",
                frequency.name().toLowerCase(), startDate, endDate);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=" + filename)
                .body(csv);
    }
}
