package com.company.observability.service;

import com.company.observability.dto.*;
import com.company.observability.repository.CalculatorRunCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostAnalyticsService {

    private final CalculatorRunCostRepository costRepository;

    public CostDashboardResponse getDashboardData(String period, String calculatorId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateStartDate(period, endDate);
        
        String calcFilter = "all".equals(calculatorId) ? null : calculatorId;
        
        return CostDashboardResponse.builder()
                .dailyCosts(getDailyTrends(startDate, endDate, calcFilter))
                .calculatorCosts(getCalculatorCostSummary(startDate, endDate))
                .recentRuns(getRecentRuns(20, calcFilter, null))
                .costBreakdown(getCostBreakdown(startDate, endDate))
                .efficiency(getEfficiencyMetrics(startDate, endDate))
                .period(period)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    public List<DailyCostTrend> getDailyTrends(LocalDate startDate, LocalDate endDate, String calculatorId) {
        if (calculatorId == null) {
            return costRepository.getDailyCostTrendsAllCalculators(startDate, endDate);
        } else {
            return costRepository.getDailyCostTrendsByCalculator(startDate, endDate, calculatorId);
        }
    }

    public List<CalculatorCostSummary> getCalculatorCostSummary(LocalDate startDate, LocalDate endDate) {
        return costRepository.getCalculatorCostSummary(startDate, endDate);
    }

    public List<RecentRunCost> getRecentRuns(int limit, String calculatorId, String status) {
        return costRepository.getRecentRunsWithCost(limit, calculatorId, status);
    }

    public CostBreakdown getCostBreakdown(LocalDate startDate, LocalDate endDate) {
        return costRepository.getCostBreakdown(startDate, endDate);
    }

    public EfficiencyMetrics getEfficiencyMetrics(LocalDate startDate, LocalDate endDate) {
        return costRepository.getEfficiencyMetrics(startDate, endDate);
    }

    public List<CalculatorCostSummary> getTopExpensiveCalculators(int limit, LocalDate startDate, LocalDate endDate) {
        return costRepository.getCalculatorCostSummary(startDate, endDate).stream()
                .sorted((a, b) -> b.getTotalCost().compareTo(a.getTotalCost()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<CostAnomaly> getCostAnomalies(double threshold, LocalDate startDate, LocalDate endDate) {
        return costRepository.getCostAnomalies(threshold, startDate, endDate);
    }

    public byte[] exportToCsv(LocalDate startDate, LocalDate endDate, String calculatorId) {
        List<RecentRunCost> runs = costRepository.getAllRunsForExport(startDate, endDate, calculatorId);

        StringBuilder csv = new StringBuilder();
        csv.append("Run ID,Calculator ID,Calculator Name,Start Time,End Time,Duration (sec),")
                .append("Status,DBU Cost,VM Cost,Storage Cost,Total Cost,Worker Count,Spot Instance,Photon Enabled\n");

        for (RecentRunCost run : runs) {
            csv.append(String.format("%s,%s,%s,%s,%s,%d,%s,%.2f,%.2f,%.2f,%.2f,%d,%s,%s\n",
                    run.getRunId(),
                    run.getCalculatorId(),
                    run.getCalculatorName(),
                    run.getStartTime(),
                    run.getEndTime(),
                    run.getDurationSeconds() != null ? run.getDurationSeconds() : 0,
                    run.getStatus(),
                    run.getDbuCost() != null ? run.getDbuCost() : BigDecimal.ZERO,
                    run.getVmCost() != null ? run.getVmCost() : BigDecimal.ZERO,
                    run.getStorageCost() != null ? run.getStorageCost() : BigDecimal.ZERO,
                    run.getTotalCost() != null ? run.getTotalCost() : BigDecimal.ZERO,
                    run.getWorkerCount() != null ? run.getWorkerCount().intValue() : 0,
                    run.getSpotInstance() != null ? run.getSpotInstance() : false,
                    run.getPhotonEnabled() != null ? run.getPhotonEnabled() : false
            ));
        }

        return csv.toString().getBytes();
    }


    private LocalDate calculateStartDate(String period, LocalDate endDate) {
        return switch (period) {
            case "7d" -> endDate.minus(7, ChronoUnit.DAYS);
            case "30d" -> endDate.minus(30, ChronoUnit.DAYS);
            case "90d" -> endDate.minus(90, ChronoUnit.DAYS);
            default -> endDate.minus(30, ChronoUnit.DAYS);
        };
    }
}
