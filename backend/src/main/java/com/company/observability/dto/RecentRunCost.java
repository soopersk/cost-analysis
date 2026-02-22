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

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentRunCost {

    private Long         runId;
    private String       calculatorId;
    private String       calculatorName;
    private RunFrequency frequency;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate    reportingDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;

    private Integer    durationSeconds;
    private String     status;
    private BigDecimal dbuCost;
    private BigDecimal vmCost;
    private BigDecimal storageCost;
    private BigDecimal totalCost;
    private Integer    workerCount;
    private Boolean    spotInstances;
    private Boolean    photonEnabled;
    private String     tenantAbb;

    public BigDecimal getCostPerMinute() {
        if (durationSeconds == null || durationSeconds == 0 || totalCost == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal minutes = BigDecimal.valueOf(durationSeconds)
                .divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
        return totalCost.divide(minutes, 6, RoundingMode.HALF_UP);
    }
}
