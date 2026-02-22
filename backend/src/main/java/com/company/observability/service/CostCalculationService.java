package com.company.observability.service;

import com.company.observability.domain.CalculatorRunCost;
import com.company.observability.domain.CostCalculationResult;
import com.company.observability.dto.CostCalculationRunResult;
import com.company.observability.repository.CostCalculationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates the cost calculation lifecycle.
 *
 * <h3>Execution modes</h3>
 * <ul>
 *   <li><b>Scheduled</b>: runs daily at 02:00 UTC via {@code @Scheduled}.
 *       Processes all PENDING rows without resetting existing results.</li>
 *   <li><b>Ad-hoc</b>: called from the REST controller. Optionally resets
 *       CALCULATED or FAILED rows back to PENDING before processing.</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * An {@link AtomicBoolean} prevents two executions from overlapping within the
 * same JVM. The repository uses {@code FOR UPDATE SKIP LOCKED} as a second
 * guard for multi-instance deployments — each instance grabs a non-overlapping
 * slice of PENDING rows.
 *
 * <h3>Batch processing</h3>
 * Rows are processed in batches of {@value #BATCH_SIZE}. Each batch is
 * committed independently — a mid-run failure never rolls back already-computed
 * rows. Within a batch, a per-row failure marks that row FAILED and moves on;
 * it never aborts the rest of the batch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostCalculationService {

    private static final int BATCH_SIZE = 500;

    private final CostCalculationRepository calcRepository;
    private final CostCalculationEngine     engine;

    /** Guards against concurrent scheduled + ad-hoc executions. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    // =========================================================================
    // Scheduled — daily at 02:00 UTC
    // =========================================================================

    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledRun() {
        log.info("Scheduled cost calculation triggered at {}", LocalDateTime.now());
        if (!running.compareAndSet(false, true)) {
            log.warn("Scheduled run skipped — a calculation is already in progress");
            return;
        }
        try {
            CostCalculationRunResult result = executeCalculation();
            log.info("Scheduled run complete: {}", result.summary());
        } finally {
            running.set(false);
        }
    }

    // =========================================================================
    // Ad-hoc — called from REST controller
    // =========================================================================

    /**
     * @param resetCalculated recompute CALCULATED rows (use after a rate constant update)
     * @param resetFailed     retry FAILED rows (use after fixing a data or config issue)
     */
    public CostCalculationRunResult runAdhoc(boolean resetCalculated, boolean resetFailed) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "A cost calculation run is already in progress. Try again shortly.");
        }
        try {
            int reset = 0;
            if (resetCalculated || resetFailed) {
                reset = calcRepository.resetToPending(resetCalculated, resetFailed);
                log.info("Reset {} rows to PENDING (resetCalculated={}, resetFailed={})",
                        reset, resetCalculated, resetFailed);
            }
            return executeCalculation().withRowsReset(reset);
        } finally {
            running.set(false);
        }
    }

    /** Queue depth snapshot — does not trigger any work. */
    public CostCalculationRunResult getStatus() {
        return CostCalculationRunResult.builder()
                .isRunning(running.get())
                .pendingRows(calcRepository.countPending())
                .failedRows(calcRepository.countFailed())
                .rowsProcessed(0).rowsCalculated(0).rowsSkipped(0).rowsFailed(0).rowsReset(0)
                .build();
    }

    // =========================================================================
    // Execution loop
    // =========================================================================

    private CostCalculationRunResult executeCalculation() {
        LocalDateTime startedAt  = LocalDateTime.now();
        int totalProcessed  = 0, totalCalculated = 0, totalSkipped = 0, totalFailed = 0;

        log.info("Cost calculation started. Pending: {}", calcRepository.countPending());

        List<CalculatorRunCost> batch;
        do {
            batch = calcRepository.fetchPendingBatch(BATCH_SIZE);
            if (batch.isEmpty()) break;

            BatchOutcome outcome = processBatch(batch);
            totalProcessed  += outcome.processed();
            totalCalculated += outcome.calculated();
            totalSkipped    += outcome.skipped();
            totalFailed     += outcome.failed();

            log.info("Batch: processed={} calculated={} skipped={} failed={} | running_total={}",
                    outcome.processed(), outcome.calculated(),
                    outcome.skipped(), outcome.failed(), totalProcessed);

        } while (batch.size() == BATCH_SIZE);

        LocalDateTime completedAt = LocalDateTime.now();
        log.info("Calculation complete in {}ms: processed={} calculated={} skipped={} failed={}",
                java.time.Duration.between(startedAt, completedAt).toMillis(),
                totalProcessed, totalCalculated, totalSkipped, totalFailed);

        return CostCalculationRunResult.builder()
                .startedAt(startedAt)
                .completedAt(completedAt)
                .isRunning(false)
                .pendingRows(calcRepository.countPending())
                .failedRows(calcRepository.countFailed())
                .rowsProcessed(totalProcessed)
                .rowsCalculated(totalCalculated)
                .rowsSkipped(totalSkipped)
                .rowsFailed(totalFailed)
                .rowsReset(0)
                .build();
    }

    /**
     * One batch: compute costs for eligible rows, bulk-write successes,
     * write failures and skips individually.
     * Runs in its own transaction so a crash here doesn't roll back prior batches.
     */
    @Transactional
    protected BatchOutcome processBatch(List<CalculatorRunCost> batch) {
        List<CostCalculationResult> successes = new ArrayList<>();
        int skipped = 0, failed = 0;

        for (CalculatorRunCost run : batch) {
            if (isIneligible(run)) {
                calcRepository.markSkipped(run.getRunId(), skipReason(run));
                skipped++;
                continue;
            }
            try {
                // Engine is pure — no DB calls, no side effects.
                successes.add(engine.calculate(run));
            } catch (Exception ex) {
                String msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                calcRepository.markFailed(run.getRunId(), msg);
                failed++;
                log.error("run_id={} calculation failed", run.getRunId(), ex);
            }
        }

        if (!successes.isEmpty()) {
            calcRepository.markCalculatedBatch(successes);
        }

        return new BatchOutcome(batch.size(), successes.size(), skipped, failed);
    }

    // =========================================================================
    // Eligibility
    // =========================================================================

    /**
     * CANCELED runs never started a cluster — no cost incurred, mark SKIPPED.
     * FAILED and TIMEDOUT runs with non-zero duration DID incur cost and ARE priced.
     */
    private boolean isIneligible(CalculatorRunCost run) {
        if ("CANCELED".equalsIgnoreCase(run.getStatus())) return true;
        if (run.getDurationHours() == null) return true;
        if (run.getDurationHours().compareTo(BigDecimal.ZERO) <= 0) return true;
        if (run.getDriverNodeType() == null || run.getDriverNodeType().isBlank()) return true;
        return false;
    }

    private String skipReason(CalculatorRunCost run) {
        if ("CANCELED".equalsIgnoreCase(run.getStatus()))
            return "CANCELED — cluster never started, no cost incurred";
        if (run.getDurationHours() == null || run.getDurationHours().compareTo(BigDecimal.ZERO) <= 0)
            return "duration_hours is zero or null — no compute time to price";
        return "driver_node_type is missing — cannot determine pricing";
    }

    record BatchOutcome(int processed, int calculated, int skipped, int failed) {}
}