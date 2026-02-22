package com.company.observability.dto;

import com.company.observability.domain.RunFrequency;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A run whose cost significantly exceeds the per-(calculator, frequency)
 * average. Anomaly detection is always scoped to the same frequency — a
 * MONTHLY run is never compared against DAILY run averages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostAnomaly {

    private Long         runId;
    private String       calculatorId;
    private String       calculatorName;
    private RunFrequency frequency;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate    reportingDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    private BigDecimal actualCost;
    private BigDecimal averageCost;
    private BigDecimal costMultiplier;
    private String     status;
    private Integer    durationSeconds;

    public BigDecimal getVariancePercent() {
        if (averageCost == null || averageCost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return actualCost.subtract(averageCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(averageCost, 2, RoundingMode.HALF_UP);
    }
}
