package com.company.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

// Recent run with cost details
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentRunCost {
    private String runId;
    private String calculatorId;
    private String calculatorName;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
    
    private Integer durationSeconds;
    private String status;
    private BigDecimal dbuCost;
    private BigDecimal vmCost;
    private BigDecimal storageCost;
    private BigDecimal totalCost;
    private BigDecimal workerCount;
    private Boolean spotInstance;
    private Boolean photonEnabled;
    private Boolean isRetry;
    private Integer attemptNumber;
    
    // Computed properties
    public BigDecimal getCostPerMinute() {
        if (durationSeconds == null || durationSeconds == 0) return BigDecimal.ZERO;
        return totalCost.multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(durationSeconds), 6, RoundingMode.HALF_UP);
    }
}
