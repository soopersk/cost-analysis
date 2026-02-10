package com.company.observability.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRunDetails {

    private int runId;
    private int jobId;
    private String jobName;

    // From custom_tags["task-id"]
    private String calculatorId;

    // From custom_tags["task-name"]
    private String calculatorName;

    private String clusterId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssX", timezone = "UTC")
    private Instant startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssX", timezone = "UTC")
    private Instant endTime;

    private int durationSeconds;

    // Duration in hours (calculated from durationSeconds)
    private double durationHours;

    private String status;

    private String driverNodeType;
    private String workerNodeType;
    private int workerCount;

    private boolean spotInstancesUsed;
    private boolean photonEnabled;

    // Databricks Runtime version (e.g., "14.3.x-scala2.12")
    private String sparkVersion;

    // "STANDARD" or "PHOTON"
    private String runtimeEngine;

    // "JOB", "UI", "API"
    private String clusterSource;

    // "Jobs Compute" or "All-Purpose Compute"
    private String workloadType;

    // Azure region (e.g., "eastus2")
    private String region;

    private boolean autoscaleEnabled;
    private Integer autoscaleMinWorkers;
    private Integer autoscaleMaxWorkers;

    // Custom tags from cluster configuration
    // From custom_tags["task-version"]
    private String taskVersion;

    // From custom_tags["context-id"]
    private String contextId;

    // From custom_tags["megdp-run-id"]
    private String megdpRunId;

    // From custom_tags["tenant-abb"]
    private String tenantAbb;

    private BigDecimal totalCostUsd;
    private BigDecimal dbuCostUsd;
    private BigDecimal vmCostUsd;
    private BigDecimal storageCostUsd;

    private OffsetDateTime collectionTimestamp;

    // Lombok generates getters/setters
}
