package com.company.observability.dto;

import com.company.observability.domain.RunFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

// Main dashboard response
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostDashboardResponse {
    private RunFrequency frequency;
    private List<DailyCostTrend> dailyCosts;
    private List<CalculatorCostSummary> calculatorCosts;
    private List<RecentRunCost> recentRuns;
    private CostBreakdown costBreakdown;
    private EfficiencyMetrics efficiency;
    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
}
