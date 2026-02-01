package com.company.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostBreakdownItem {
    private String name;
    private BigDecimal value;
    private String color;
}
