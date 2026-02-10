package com.company.observability.controller;

import com.company.observability.domain.JobRunDetails;
import com.company.observability.service.JobRunCostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/calculator-runs")
@RequiredArgsConstructor
public class JobRunCostController {

    private final JobRunCostService jobRunCostService;

    /**
     * Ingest job run details and calculate costs on the backend.
     */
    @PostMapping("/costs")
    public ResponseEntity<Map<String, Object>> ingestJobRunCosts(
            @RequestBody List<JobRunDetails> runs
    ) {
        int processed = jobRunCostService.upsertJobRuns(runs);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "processed", processed
        ));
    }
}
