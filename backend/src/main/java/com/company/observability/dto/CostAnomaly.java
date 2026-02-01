package com.company.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

// Cost anomaly
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostAnomaly {
    private String runId;
    private String calculatorId;
    private String calculatorName;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;
    
    private BigDecimal actualCost;
    private BigDecimal averageCost;
    private BigDecimal costMultiplier;
    private String status;
    private Integer durationSeconds;
    
    // Computed
    public BigDecimal getVariancePercent() {
        if (averageCost.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return actualCost.subtract(averageCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(averageCost, 2, RoundingMode.HALF_UP);
    }
}
