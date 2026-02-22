package com.company.observability.dto;

import com.company.observability.domain.RunFrequency;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated cost summary for one (calculator, frequency) pair.
 *
 * <p>A calculator that runs at both cadences produces two independent
 * summary rows — one for DAILY and one for MONTHLY. They are never merged.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalculatorCostSummary {
    private String       calculatorId;
    private String       calculatorName;

    /**
     * The scheduling cadence this summary covers.
     * Queries must group by (calculator_id, frequency) to produce separate
     * rows for DAILY and MONTHLY runs of the same calculator.
     */
    private RunFrequency frequency;

    private Long       totalRuns;
    private BigDecimal totalCost;
    private BigDecimal avgCostPerRun;
    private BigDecimal dbuCost;
    private BigDecimal vmCost;
    private BigDecimal storageCost;
    private Long       successfulRuns;
    private Long       failedRuns;
    private Long       spotInstanceRuns;
    private Long       photonEnabledRuns;

    // ── Derived metrics ──────────────────────────────────────────────────────

    public BigDecimal getSuccessRate() {
        if (totalRuns == null || totalRuns == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(successfulRuns)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRuns), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getFailureRate() {
        if (totalRuns == null || totalRuns == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(failedRuns)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRuns), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getSpotUsagePercent() {
        if (totalRuns == null || totalRuns == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(spotInstanceRuns)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRuns), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getPhotonAdoptionPercent() {
        if (totalRuns == null || totalRuns == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(photonEnabledRuns)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRuns), 2, RoundingMode.HALF_UP);
    }
}

