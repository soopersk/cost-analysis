package com.company.observability.controller;

import com.company.observability.dto.*;
import com.company.observability.service.CostAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cost-analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class CostAnalyticsController {

    private final CostAnalyticsService costAnalyticsService;

    /**
     * Get comprehensive cost analytics dashboard data
     */
    @GetMapping
    public ResponseEntity<CostDashboardResponse> getDashboardData(
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(defaultValue = "all") String calculator
    ) {
        CostDashboardResponse response = costAnalyticsService.getDashboardData(period, calculator);
        return ResponseEntity.ok(response);
    }

    /**
     * Get daily cost trends
     */
    @GetMapping("/daily-trends")
    public ResponseEntity<List<DailyCostTrend>> getDailyTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String calculatorId
    ) {
        List<DailyCostTrend> trends = costAnalyticsService.getDailyTrends(startDate, endDate, calculatorId);
        return ResponseEntity.ok(trends);
    }

    /**
     * Get calculator-level cost summary
     */
    @GetMapping("/by-calculator")
    public ResponseEntity<List<CalculatorCostSummary>> getCalculatorCosts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<CalculatorCostSummary> summary = costAnalyticsService.getCalculatorCostSummary(startDate, endDate);
        return ResponseEntity.ok(summary);
    }

    /**
     * Get recent calculator runs with cost details
     */
    @GetMapping("/recent-runs")
    public ResponseEntity<List<RecentRunCost>> getRecentRuns(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String calculatorId,
            @RequestParam(required = false) String status
    ) {
        List<RecentRunCost> runs = costAnalyticsService.getRecentRuns(limit, calculatorId, status);
        return ResponseEntity.ok(runs);
    }

    /**
     * Get cost breakdown (DBU, VM, Storage, Network)
     */
    @GetMapping("/breakdown")
    public ResponseEntity<CostBreakdown> getCostBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        CostBreakdown breakdown = costAnalyticsService.getCostBreakdown(startDate, endDate);
        return ResponseEntity.ok(breakdown);
    }

    /**
     * Get efficiency metrics
     */
    @GetMapping("/efficiency")
    public ResponseEntity<EfficiencyMetrics> getEfficiencyMetrics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        EfficiencyMetrics metrics = costAnalyticsService.getEfficiencyMetrics(startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Export cost data to CSV
     */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportToCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String calculatorId
    ) {
        byte[] csvData = costAnalyticsService.exportToCsv(startDate, endDate, calculatorId);
        
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=calculator-costs.csv")
                .body(csvData);
    }

    /**
     * Get top N most expensive calculators
     */
    @GetMapping("/top-expensive")
    public ResponseEntity<List<CalculatorCostSummary>> getTopExpensiveCalculators(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<CalculatorCostSummary> topExpensive = costAnalyticsService.getTopExpensiveCalculators(limit, startDate, endDate);
        return ResponseEntity.ok(topExpensive);
    }

    /**
     * Get cost anomalies (runs with unusually high costs)
     */
    @GetMapping("/anomalies")
    public ResponseEntity<List<CostAnomaly>> getCostAnomalies(
            @RequestParam(defaultValue = "3.0") double threshold,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<CostAnomaly> anomalies = costAnalyticsService.getCostAnomalies(threshold, startDate, endDate);
        return ResponseEntity.ok(anomalies);
    }
}
