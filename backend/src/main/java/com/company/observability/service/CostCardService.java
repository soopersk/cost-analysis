package com.company.observability.service;

import com.company.observability.domain.RunFrequency;
import com.company.observability.dto.CostCardResponse;
import com.company.observability.dto.CostCardResponse.*;
import com.company.observability.dto.DailyCostTrend;
import com.company.observability.dto.MonthlyCostTrend;
import com.company.observability.repository.CalculatorRunCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CostCardService {

    private static final String CURRENCY   = "USD";
    private static final int    CHART_DAYS = 90;

    private final CalculatorRunCostRepository repo;

    public CostCardResponse getCostCard(String calculatorId, RunFrequency frequency) {

        LocalDate today     = LocalDate.now();
        LocalDate chartFrom = today.minusDays(CHART_DAYS);

        // 1. Config (threshold + environment) — may be absent
        Map<String, Object> config      = repo.getCalculatorConfig(calculatorId).orElse(Map.of());
        BigDecimal          threshold   = (BigDecimal) config.get("daily_threshold_usd");
        String              environment = (String) config.get("environment");

        // 2. Previous-month and YTD totals
        Map<String, BigDecimal> totals    = repo.getCostCardTotals(calculatorId, frequency);
        BigDecimal              prevMonth = totals.get("prev_month");
        BigDecimal              ytd       = totals.get("ytd");

        // 3. Trends — fetch 13 months so the last row has non-null momPct and yoyPct
        List<MonthlyCostTrend> trend  = repo.getMonthlyCostTrend(calculatorId, frequency, 13);
        MonthlyCostTrend       latest = trend.isEmpty() ? null : trend.get(trend.size() - 1);

        // 4. Chart data — last 90 days
        List<DailyCostTrend> dailySeries =
                repo.getCostTrendByCalculator(frequency, chartFrom, today, calculatorId);

        // 5. Calculator name — from trend history, fall back to calculatorId
        String calcName = trend.isEmpty() ? calculatorId : trend.get(0).getCalculatorName();

        return CostCardResponse.builder()
                .calculatorId(calculatorId)
                .calculatorName(calcName)
                .environment(environment)
                .currency(CURRENCY)
                .navigable(true)
                .threshold(buildThreshold(threshold))
                .summary(buildSummary(prevMonth, ytd, today, latest))
                .chart(buildChart(dailySeries))
                .build();
    }

    // -------------------------------------------------------------------------

    private Threshold buildThreshold(BigDecimal daily) {
        if (daily == null) return null;
        return Threshold.builder()
                .daily(daily)
                .label("Daily threshold")
                .build();
    }

    private Summary buildSummary(
            BigDecimal prevMonth, BigDecimal ytd, LocalDate today, MonthlyCostTrend latest) {

        String prevMonthLabel = today.minusMonths(1).format(DateTimeFormatter.ofPattern("MMM"));
        String ytdLabel       = "Jan 1 – " + today.format(DateTimeFormatter.ofPattern("MMM d"));

        return Summary.builder()
                .monthly(PeriodAmount.builder()
                        .value(prevMonth)
                        .period(prevMonthLabel)
                        .periodType("previous_month")
                        .build())
                .yearToDate(PeriodAmount.builder()
                        .value(ytd)
                        .period(ytdLabel)
                        .periodType("year_to_date")
                        .build())
                .trends(buildTrends(latest))
                .build();
    }

    private Trends buildTrends(MonthlyCostTrend latest) {
        if (latest == null) return Trends.builder().build();
        return Trends.builder()
                .mom(toTrendItem(latest.getMomPct()))
                .yoy(toTrendItem(latest.getYoyPct()))
                .build();
    }

    /** Wraps a signed percentage — positive = up, negative = down, null = no data. */
    private TrendItem toTrendItem(BigDecimal pct) {
        if (pct == null) return null;
        return TrendItem.builder()
                .value(pct)
                .build();
    }

    private Chart buildChart(List<DailyCostTrend> series) {
        List<DataPoint> points = series.stream()
                .map(d -> DataPoint.builder()
                        .date(d.getDate())
                        .cost(d.getTotalCost())
                        .build())
                .toList();

        return Chart.builder()
                .type("area")
                .xAxisLabel("Month")
                .yAxisLabel("Cost")
                .data(points)
                .build();
    }
}
