package com.company.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.LocalDateTime;

/**
 * Response body for cost calculation run endpoints.
 * Returned immediately by the ad-hoc trigger and by the status endpoint.
 */
@Value
@Builder
public class CostCalculationRunResult {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime completedAt;

    /** True while a run is currently executing (status endpoint only). */
    boolean isRunning;

    /** Rows still waiting for calculation after this run completed. */
    long pendingRows;

    /** Rows in FAILED state after this run completed. */
    long failedRows;

    /** Total rows touched in this run (calculated + skipped + failed). */
    int rowsProcessed;

    /** Rows successfully priced and marked CALCULATED. */
    int rowsCalculated;

    /** Rows marked SKIPPED (CANCELED or zero-duration). */
    int rowsSkipped;

    /** Rows marked FAILED (pricing config missing or engine error). */
    int rowsFailed;

    /** Rows reset to PENDING before this run (force recalculation only). */
    @With
    int rowsReset;

    /** One-line summary for log output. */
    public String summary() {
        return "processed=%d calculated=%d skipped=%d failed=%d pending_remaining=%d"
                .formatted(rowsProcessed, rowsCalculated, rowsSkipped, rowsFailed, pendingRows);
    }
}