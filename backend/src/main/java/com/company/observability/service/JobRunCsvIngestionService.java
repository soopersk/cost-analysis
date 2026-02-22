package com.company.observability.service;

import com.company.observability.domain.CalculatorRunCost;
import com.company.observability.domain.RunFrequency;
import com.company.observability.dto.CsvIngestionResult;
import com.company.observability.repository.CalculatorRunCostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a Databricks-exported CSV and batch-upserts rows into
 * {@code calculator_run_costs}.
 *
 * <h3>Required CSV columns (new in v2)</h3>
 * <ul>
 *   <li>{@code frequency} — "DAILY" or "MONTHLY" (case-insensitive)</li>
 *   <li>{@code reporting_date} — ISO-8601 date, e.g. "2024-03-15"</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * ON CONFLICT (run_id) is used, so the same CSV can be reprocessed safely.
 * {@code frequency} and {@code reporting_date} are set once at first insert
 * and are never overwritten by a reprocess.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobRunCsvIngestionService {

    private static final DateTimeFormatter ISO_DATE         = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_NO_OFFSET    = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter ISO_WITH_OFFSET  = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final CalculatorRunCostRepository repository;

    @Transactional
    public CsvIngestionResult ingest(MultipartFile file) throws IOException {
        log.info("CSV ingestion started: file={}, size={} bytes",
                file.getOriginalFilename(), file.getSize());

        List<CalculatorRunCost> batch  = new ArrayList<>();
        List<String>            errors = new ArrayList<>();
        int rowNumber = 1;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                try {
                    batch.add(parseRecord(record));
                } catch (Exception ex) {
                    errors.add(String.format("Row %d: %s", rowNumber, ex.getMessage()));
                    log.warn("Parse failure on row {}: {}", rowNumber, ex.getMessage());
                }
                rowNumber++;
            }
        }

        int totalRows = rowNumber - 1;

        if (batch.isEmpty()) {
            return CsvIngestionResult.builder()
                    .totalRows(totalRows)
                    .successCount(0)
                    .failureCount(errors.size())
                    .errors(errors)
                    .build();
        }

        int chunkSize = 1000;
        for (int i = 0; i < batch.size(); i += chunkSize) {
            repository.batchUpsert(batch.subList(i, Math.min(i + chunkSize, batch.size())));
        }

        log.info("CSV ingestion complete: total={}, success={}, errors={}",
                totalRows, batch.size(), errors.size());

        return CsvIngestionResult.builder()
                .totalRows(totalRows)
                .successCount(batch.size())
                .failureCount(errors.size())
                .errors(errors)
                .build();
    }

    // -------------------------------------------------------------------------
    // Record → domain object
    // -------------------------------------------------------------------------

    private CalculatorRunCost parseRecord(CSVRecord r) {
        return CalculatorRunCost.builder()
                .runId(requireLong(r, "run_id"))
                .jobId(requireLong(r, "job_id"))
                .jobName(str(r, "job_name"))
                .calculatorId(str(r, "calculator_id"))
                .calculatorName(str(r, "calculator_name"))
                // ── new required scheduling fields ──────────────────────────
                .frequency(requireFrequency(r, "frequency"))
                .reportingDate(requireDate(r, "reporting_date"))
                // ────────────────────────────────────────────────────────────
                .clusterId(str(r, "cluster_id"))
                .startTime(requireTimestamp(r, "start_time"))
                .endTime(requireTimestamp(r, "end_time"))
                .durationSeconds(requireInt(r, "duration_seconds"))
                .durationHours(requireDecimal(r, "duration_hours"))
                .status(str(r, "status"))
                .driverNodeType(str(r, "driver_node_type"))
                .workerNodeType(str(r, "worker_node_type"))
                .workerCount(nullableInt(r, "worker_count"))
                .spotInstances(bool(r, "spot_instances"))
                .photonEnabled(bool(r, "photon_enabled"))
                .sparkVersion(str(r, "spark_version"))
                .runtimeEngine(str(r, "runtime_engine"))
                .clusterSource(str(r, "cluster_source"))
                .workloadType(str(r, "workload_type"))
                .region(str(r, "region"))
                .autoscaleEnabled(bool(r, "autoscale_enabled"))
                .autoscaleMin(nullableInt(r, "autoscale_min"))
                .autoscaleMax(nullableInt(r, "autoscale_max"))
                .taskVersion(str(r, "task_version"))
                .contextId(str(r, "context_id"))
                .megdpRunId(str(r, "megdp_run_id"))
                .tenantAbb(str(r, "tenant_abb"))
                .dbuCostUsd(nullableDecimal(r, "dbu_cost_usd"))
                .vmCostUsd(nullableDecimal(r, "vm_cost_usd"))
                .storageCostUsd(nullableDecimal(r, "storage_cost_usd"))
                .totalCostUsd(nullableDecimal(r, "total_cost_usd"))
                .build();
    }

    // -------------------------------------------------------------------------
    // Typed extractors
    // -------------------------------------------------------------------------

    private String str(CSVRecord r, String col) {
        String v = r.get(col);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    /**
     * Parses and validates the {@code frequency} column.
     * Accepts "DAILY", "MONTHLY" (case-insensitive).
     */
    private RunFrequency requireFrequency(CSVRecord r, String col) {
        String v = str(r, col);
        if (v == null) throw new IllegalArgumentException("'" + col + "' is required");
        try {
            return RunFrequency.of(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value in '" + col + "': " + e.getMessage());
        }
    }

    /**
     * Parses the {@code reporting_date} column.
     * Accepts ISO-8601 date format: "yyyy-MM-dd".
     */
    private LocalDate requireDate(CSVRecord r, String col) {
        String v = str(r, col);
        if (v == null) throw new IllegalArgumentException("'" + col + "' is required");
        try {
            return LocalDate.parse(v, ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Cannot parse date in '" + col + "' (expected yyyy-MM-dd): " + v);
        }
    }

    private Long requireLong(CSVRecord r, String col) {
        String v = str(r, col);
        if (v == null) throw new IllegalArgumentException("'" + col + "' is required");
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long in '" + col + "': " + v);
        }
    }

    private Integer requireInt(CSVRecord r, String col) {
        String v = str(r, col);
        if (v == null) throw new IllegalArgumentException("'" + col + "' is required");
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer in '" + col + "': " + v);
        }
    }

    private Integer nullableInt(CSVRecord r, String col) {
        String v = str(r, col);
        if (v == null) return null;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer in '" + col + "': " + v);
        }
    }

    private Boolean bool(CSVRecord r, String col) {
        return Boolean.parseBoolean(str(r, col));
    }

    private BigDecimal requireDecimal(CSVRecord r, String col) {
        String v = str(r, col);
        if (v == null) throw new IllegalArgumentException("'" + col + "' is required");
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal in '" + col + "': " + v);
        }
    }

    private BigDecimal nullableDecimal(CSVRecord r, String col) {
        String v = str(r, col);
        if (v == null) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal in '" + col + "': " + v);
        }
    }

    /**
     * Handles three timestamp formats Databricks may emit:
     * epoch-ms, epoch-s, ISO-8601 with offset, ISO-8601 without offset.
     * All stored as UTC LocalDateTime.
     */
    private LocalDateTime requireTimestamp(CSVRecord r, String col) {
        String v = str(r, col);
        if (v == null) throw new IllegalArgumentException("'" + col + "' is required");

        if (v.matches("\\d{10,13}")) {
            long ms = Long.parseLong(v);
            if (ms < 100_000_000_000L) ms *= 1000;
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC);
        }
        try {
            return LocalDateTime.ofInstant(Instant.from(ISO_WITH_OFFSET.parse(v)), ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) { /* fall through */ }
        try {
            return LocalDateTime.parse(v, ISO_NO_OFFSET);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Cannot parse timestamp in '" + col + "': " + v);
        }
    }
}
