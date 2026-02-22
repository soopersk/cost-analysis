package com.company.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostBreakdown {

    private BigDecimal dbuCost;
    private BigDecimal vmCost;
    private BigDecimal storageCost;
    private BigDecimal totalCost;

    public List<CostBreakdownItem> getBreakdownItems() {
        return List.of(
                new CostBreakdownItem("DBU Compute",       dbuCost,     "#3b82f6"),
                new CostBreakdownItem("VM Infrastructure", vmCost,      "#8b5cf6"),
                new CostBreakdownItem("Storage",           storageCost, "#10b981")
        );
    }
}
