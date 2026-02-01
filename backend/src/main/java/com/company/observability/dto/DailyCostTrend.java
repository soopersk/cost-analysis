package com.company.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

// Daily cost trend
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCostTrend {
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    
    private BigDecimal totalCost;
    private BigDecimal dbuCost;
    private BigDecimal vmCost;
    private BigDecimal storageCost;
    private BigDecimal networkCost;
}
