package com.company.observability.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Response returned by the CSV ingestion endpoint.
 */
@Value
@Builder
public class CsvIngestionResult {

    /** Total rows read from the CSV file (excluding header). */
    int totalRows;

    /** Number of rows successfully inserted or updated. */
    int successCount;

    /** Number of rows that failed to parse or persist. */
    int failureCount;

    /**
     * Human-readable description of each failed row.
     * Format: "Row <n>: <reason>"
     */
    List<String> errors;
}