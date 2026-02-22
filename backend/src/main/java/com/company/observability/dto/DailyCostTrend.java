package com.company.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cost totals grouped by reporting_date.
 * Always scoped to a single frequency — pass frequency as a filter
 * when querying so DAILY and MONTHLY series are kept separate.
 */
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
}
