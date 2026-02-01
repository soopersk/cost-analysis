package com.company.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// Cost breakdown by component
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostBreakdown {
    private BigDecimal dbuCost;
    private BigDecimal vmCost;
    private BigDecimal storageCost;
    private BigDecimal networkCost;
    private BigDecimal totalCost;
    
    public List<CostBreakdownItem> getBreakdownItems() {
        return List.of(
            new CostBreakdownItem("DBU Compute", dbuCost, "#3b82f6"),
            new CostBreakdownItem("VM Infrastructure", vmCost, "#8b5cf6"),
            new CostBreakdownItem("Storage", storageCost, "#10b981"),
            new CostBreakdownItem("Network", networkCost, "#f59e0b")
        );
    }
}
