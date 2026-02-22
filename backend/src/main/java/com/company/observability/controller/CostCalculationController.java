package com.company.observability.controller;

import com.company.observability.dto.CostCalculationRunResult;
import com.company.observability.service.CostCalculationEngine;
import com.company.observability.service.CostCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/**
 * REST API for the cost calculation service.
 *
 * <pre>
 *   POST /api/v1/cost-calculation/run
 *        Trigger an ad-hoc calculation pass.
 *        ?resetCalculated=true  — recompute CALCULATED rows (use after a rate edit + redeploy)
 *        ?resetFailed=true      — retry FAILED rows (default: true)
 *
 *   GET  /api/v1/cost-calculation/status
 *        Queue depth + whether a run is currently in progress.
 *
 *   GET  /api/v1/cost-calculation/pricing
 *        The hardcoded rates currently in effect, read directly from
 *        CostCalculationEngine. Useful for confirming what rates are live
 *        without reading source code or tailing logs.
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/cost-calculation")
@RequiredArgsConstructor
@Slf4j
public class CostCalculationController {

    private final CostCalculationService calculationService;

    // =========================================================================
    // Trigger
    // =========================================================================

    /**
     * Triggers an ad-hoc calculation pass and blocks until it completes.
     *
     * <p>Returns 409 Conflict if a run (scheduled or ad-hoc) is already in
     * progress — the service is healthy but busy, so 409 is more precise than 503.
     *
     * <p>Use {@code resetCalculated=true} after editing a rate constant in
     * {@link CostCalculationEngine} and redeploying, to retroactively apply
     * the new rate to rows already priced under the old value.
     */
    @PostMapping("/run")
    public ResponseEntity<?> triggerRun(
            @RequestParam(defaultValue = "false") boolean resetCalculated,
            @RequestParam(defaultValue = "true")  boolean resetFailed
    ) {
        log.info("Ad-hoc calculation requested (resetCalculated={}, resetFailed={})",
                resetCalculated, resetFailed);
        try {
            return ResponseEntity.ok(
                    calculationService.runAdhoc(resetCalculated, resetFailed));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "calculation_in_progress", "message", ex.getMessage()));
        }
    }

    // =========================================================================
    // Status
    // =========================================================================

    /**
     * Returns queue depth without triggering any work.
     * Safe to poll from a monitoring dashboard.
     */
    @GetMapping("/status")
    public ResponseEntity<CostCalculationRunResult> getStatus() {
        return ResponseEntity.ok(calculationService.getStatus());
    }

    // =========================================================================
    // Active rates
    // =========================================================================

    /**
     * Returns the hardcoded rates currently in effect, read directly from
     * {@link CostCalculationEngine}'s public constants.
     *
     * <p>This replaces the old {@code /pricing/nodes} and {@code /pricing/dbu}
     * endpoints that backed onto database tables. With rates hardcoded in the
     * engine, this endpoint is the authoritative answer to "what rates will the
     * next calculation run use?" — no source code or log-diving required.
     *
     * <p>VM rates are on-demand per-node per-hour. Effective worker rate when
     * {@code spot_instances=true} is {@code vmRate × spotFraction}.
     * Effective DBU rate when {@code photon_enabled=true} is
     * {@code dbuRate × photonDbuMultiplier}.
     *
     * <p>Example response:
     * <pre>
     * {
     *   "scalars": {
     *     "dbuPricePerUnit":      0.176,
     *     "photonDbuMultiplier":  2.0,
     *     "storageRatePerHour":   0.10,
     *     "spotFraction":         0.30
     *   },
     *   "vmHourlyRates": {
     *     "Standard_D8as_v4":   0.4280,
     *     "Standard_D16as_v4":  0.8560,
     *     ...
     *   },
     *   "dbuRatesPerNodeHour": {
     *     "Standard_D8as_v4":   1.50,
     *     "Standard_D16as_v4":  3.00,
     *     ...
     *   }
     * }
     * </pre>
     */
    @GetMapping("/pricing")
    public ResponseEntity<Map<String, Object>> getPricing() {
        // TreeMap so keys are sorted alphabetically — deterministic, easy to diff.
        Map<String, BigDecimal> vmRates  = new TreeMap<>(CostCalculationEngine.VM_HOURLY_RATES);
        Map<String, BigDecimal> dbuRates = new TreeMap<>(CostCalculationEngine.DBU_RATES);

        Map<String, Object> scalars = Map.of(
                "dbuPricePerUnit",     CostCalculationEngine.DBU_PRICE_PER_UNIT,
                "photonDbuMultiplier", CostCalculationEngine.PHOTON_DBU_MULTIPLIER,
                "storageRatePerHour",  CostCalculationEngine.STORAGE_RATE_PER_HOUR,
                "spotFraction",        CostCalculationEngine.SPOT_FRACTION
        );

        return ResponseEntity.ok(Map.of(
                "scalars",             scalars,
                "vmHourlyRates",       vmRates,
                "dbuRatesPerNodeHour", dbuRates
        ));
    }
}