package com.company.observability.controller;

import com.company.observability.domain.RunFrequency;
import com.company.observability.dto.CostCardResponse;
import com.company.observability.service.CostCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Cost card API — one card per calculator.
 *
 * <pre>
 *   GET /api/v1/cost-cards/{calculatorId}?frequency=DAILY
 * </pre>
 *
 * Returns the full cost card payload: identity, threshold, previous-month/YTD
 * summary, MoM/YoY trends, and a 90-day daily cost series for the chart.
 */
@RestController
@RequestMapping("/api/v1/cost-cards")
@RequiredArgsConstructor
public class CostCardController {

    private final CostCardService costCardService;

    @GetMapping("/{calculatorId}")
    public ResponseEntity<CostCardResponse> getCostCard(
            @PathVariable                         String       calculatorId,
            @RequestParam(defaultValue = "DAILY") RunFrequency frequency
    ) {
        return ResponseEntity.ok(costCardService.getCostCard(calculatorId, frequency));
    }
}
