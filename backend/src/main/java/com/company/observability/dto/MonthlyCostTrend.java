package com.company.observability.dto;

import com.company.observability.domain.RunFrequency;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Month-over-month cost trend for one (calculator, frequency) pair.
 *
 * <p>{@code month} is formatted "YYYY-MM" for direct chart axis use.
 * Because DAILY and MONTHLY runs of the same calculator have completely
 * different cost scales and run counts, they are always returned in separate
 * series — never mixed into a single list.
 *
 * <p>Delta fields ({@code momDelta}/{@code momPct} and
 * {@code yoyDelta}/{@code yoyPct}) are {@code null} for the earliest months
 * where no prior-period data exists. Callers requesting YoY comparisons
 * should use {@code months >= 13} to get at least one non-null YoY value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyCostTrend {

    /** Calendar month of the reporting_date, e.g. "2024-03". */
    private String month;

    private String       calculatorId;
    private String       calculatorName;
    private RunFrequency frequency;
    private Long         totalRuns;
    private BigDecimal   totalCost;
    private BigDecimal   avgCostPerRun;
    private BigDecimal   minCost;
    private BigDecimal   maxCost;

    /** Absolute cost change vs. prior calendar month (null for the first month). */
    private BigDecimal momDelta;
    /** Percentage cost change vs. prior calendar month, e.g. 12.50 = +12.5%. */
    private BigDecimal momPct;
    /** Absolute cost change vs. the same month in the prior year (null when < 13 months of history). */
    private BigDecimal yoyDelta;
    /** Percentage cost change vs. the same month in the prior year. */
    private BigDecimal yoyPct;
}
