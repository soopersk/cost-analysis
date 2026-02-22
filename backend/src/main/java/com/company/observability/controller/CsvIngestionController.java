package com.company.observability.controller;

import com.company.observability.dto.CsvIngestionResult;
import com.company.observability.service.JobRunCsvIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST API for ingesting Databricks-exported CSV files into {@code calculator_run_costs}.
 *
 * <pre>
 *   POST /api/v1/ingest/csv
 *        multipart/form-data; field name = "file"
 *
 *        Returns CsvIngestionResult with row counts and any per-row parse errors.
 *        Idempotent — re-uploading the same CSV is safe (ON CONFLICT upsert).
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
@Slf4j
public class CsvIngestionController {

    private final JobRunCsvIngestionService ingestionService;

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvIngestionResult> ingestCsv(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("CSV upload received: name={}, size={} bytes",
                file.getOriginalFilename(), file.getSize());
        CsvIngestionResult result = ingestionService.ingest(file);
        return ResponseEntity.ok(result);
    }
}
