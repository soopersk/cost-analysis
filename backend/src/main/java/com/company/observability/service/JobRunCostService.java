package com.company.observability.service;

import com.company.observability.domain.CalculatorRunCost;
import com.company.observability.domain.JobRunDetails;
import com.company.observability.repository.CalculatorRunCostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobRunCostService {

    private static final BigDecimal DBU_PRICE = new BigDecimal("0.176");
    private static final BigDecimal DBU_PER_NODE_PER_HOUR = new BigDecimal("4");
    private static final String CALCULATION_VERSION = "backend-v1";
    private static final String CALCULATED_BY = "backend-service";
    private static final String ALLOCATION_METHOD = "job_run_details";

    private static final Map<String, BigDecimal> VM_HOURLY_RATES = Map.ofEntries(
            Map.entry("Standard_D8as_v4", new BigDecimal("0.428")),
            Map.entry("Standard_D16as_v4", new BigDecimal("0.856")),
            Map.entry("Standard_D32as_v4", new BigDecimal("1.712")),
            Map.entry("Standard_D8ds_v5", new BigDecimal("0.0504")),
            Map.entry("Standard_D16ds_v5", new BigDecimal("1.008")),
            Map.entry("Standard_D32ds_v5", new BigDecimal("2.016")),
            Map.entry("Standard_DBds_v4", new BigDecimal("0.0504")),
            Map.entry("Standard_D16ds_v4", new BigDecimal("1.008")),
            Map.entry("Standard_D32ds_v4", new BigDecimal("2.016")),
            Map.entry("Standard_E4as_v4", new BigDecimal("0.282")),
            Map.entry("Standard_E8as_v4", new BigDecimal("0.564")),
            Map.entry("Standard_E16as_v4", new BigDecimal("1.128")),
            Map.entry("Standard_E4ds_v4", new BigDecimal("0.320")),
            Map.entry("Standard_E8ds_v4", new BigDecimal("0.640")),
            Map.entry("Standard_E16ds_v4", new BigDecimal("1.280")),
            Map.entry("Standard_E4ds_v5", new BigDecimal("0.320")),
            Map.entry("Standard_E8ds_v5", new BigDecimal("0.640")),
            Map.entry("Standard_E16ds_v5", new BigDecimal("1.280"))
    );

    private final CalculatorRunCostRepository costRepository;

    public int upsertJobRuns(List<JobRunDetails> runs) {
        List<CalculatorRunCost> costs = runs.stream()
                .filter(Objects::nonNull)
                .map(this::toCalculatorRunCost)
                .collect(Collectors.toList());

        if (costs.isEmpty()) {
            return 0;
        }

        costRepository.batchUpsert(costs);
        return costs.size();
    }

    public CalculatorRunCost toCalculatorRunCost(JobRunDetails details) {
        BigDecimal durationHours = calculateDurationHours(details);
        int workerCount = details.getWorkerCount();
        int totalNodes = workerCount + 1;

        BigDecimal driverRate = lookupVmRate(details.getDriverNodeType());
        BigDecimal workerRate = lookupVmRate(details.getWorkerNodeType());

        BigDecimal driverVmCost = driverRate.multiply(durationHours);
        BigDecimal workerVmCost = workerRate
                .multiply(BigDecimal.valueOf(workerCount))
                .multiply(durationHours);
        BigDecimal totalVmCost = driverVmCost.add(workerVmCost);

        BigDecimal dbuUnits = durationHours
                .multiply(BigDecimal.valueOf(totalNodes))
                .multiply(DBU_PER_NODE_PER_HOUR);
        BigDecimal dbuCost = dbuUnits.multiply(DBU_PRICE);

        BigDecimal storageCost = Optional.ofNullable(details.getStorageCostUsd())
                .orElse(BigDecimal.ZERO);
        BigDecimal totalCost = totalVmCost.add(dbuCost).add(storageCost);

        LocalDateTime runStart = toUtc(details.getStartTime());
        LocalDateTime runEnd = toUtc(details.getEndTime());
        LocalDate reportingDate = runStart != null ? runStart.toLocalDate() : LocalDate.now(ZoneOffset.UTC);

        Integer minWorkers = null;
        Integer maxWorkers = null;
        BigDecimal avgWorkers = null;
        Integer peakWorkers = null;
        if (details.isAutoscaleEnabled()) {
            minWorkers = details.getAutoscaleMinWorkers();
            maxWorkers = details.getAutoscaleMaxWorkers();
            if (minWorkers != null && maxWorkers != null) {
                avgWorkers = BigDecimal.valueOf(minWorkers + maxWorkers)
                        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            }
            peakWorkers = maxWorkers;
        } else {
            avgWorkers = BigDecimal.valueOf(workerCount);
            peakWorkers = workerCount;
        }

        String calculatorRunId = details.getMegdpRunId();
        if (calculatorRunId == null || calculatorRunId.isBlank()) {
            calculatorRunId = String.valueOf(details.getRunId());
        }

        CalculatorRunCost cost = new CalculatorRunCost();
        cost.setCalculatorRunId(calculatorRunId);
        cost.setReportingDate(reportingDate);
        cost.setCalculatorId(details.getCalculatorId());
        cost.setCalculatorName(details.getCalculatorName());
        cost.setDatabricksRunId((long) details.getRunId());
        cost.setJobId((long) details.getJobId());
        cost.setJobName(details.getJobName());
        cost.setClusterId(details.getClusterId());
        cost.setDriverNodeType(details.getDriverNodeType());
        cost.setWorkerNodeType(details.getWorkerNodeType());
        cost.setWorkerCount(workerCount);
        cost.setMinWorkers(minWorkers);
        cost.setMaxWorkers(maxWorkers);
        cost.setAvgWorkerCount(avgWorkers);
        cost.setPeakWorkerCount(peakWorkers);
        cost.setSpotInstanceUsed(details.isSpotInstancesUsed());
        cost.setPhotonEnabled(details.isPhotonEnabled());
        cost.setAutoscaleEnabled(details.isAutoscaleEnabled());
        cost.setAutoscaleMinWorkers(details.getAutoscaleMinWorkers());
        cost.setAutoscaleMaxWorkers(details.getAutoscaleMaxWorkers());
        cost.setRunStartTime(runStart);
        cost.setRunEndTime(runEnd);
        cost.setDurationSeconds(details.getDurationSeconds());
        cost.setDurationHours(durationHours);
        cost.setRunStatus(details.getStatus());
        cost.setIsRetry(false);
        cost.setAttemptNumber(0);
        cost.setSparkVersion(details.getSparkVersion());
        cost.setRuntimeEngine(details.getRuntimeEngine());
        cost.setClusterSource(details.getClusterSource());
        cost.setWorkloadType(details.getWorkloadType());
        cost.setRegion(details.getRegion());
        cost.setDbuUnitsConsumed(dbuUnits);
        cost.setDbuUnitPrice(DBU_PRICE);
        cost.setDbuCostUsd(dbuCost);
        cost.setDbuSku("STANDARD");
        cost.setVmDriverCostUsd(driverVmCost);
        cost.setVmWorkerCostUsd(workerVmCost);
        cost.setVmTotalCostUsd(totalVmCost);
        cost.setVmNodeHours(durationHours.multiply(BigDecimal.valueOf(totalNodes)));
        cost.setStorageCostUsd(storageCost);
        cost.setTotalCostUsd(totalCost);
        cost.setAllocationMethod(ALLOCATION_METHOD);
        cost.setConfidenceScore(BigDecimal.ONE);
        cost.setCalculationVersion(CALCULATION_VERSION);
        cost.setCalculationTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        cost.setCalculatedBy(CALCULATED_BY);
        if (details.getCollectionTimestamp() != null) {
            cost.setCollectionTimestamp(details.getCollectionTimestamp().toLocalDateTime());
        }
        cost.setTaskVersion(details.getTaskVersion());
        cost.setContextId(details.getContextId());
        cost.setMegdpRunId(details.getMegdpRunId());
        cost.setTenantAbb(details.getTenantAbb());
        return cost;
    }

    private BigDecimal calculateDurationHours(JobRunDetails details) {
        if (details.getDurationSeconds() > 0) {
            return BigDecimal.valueOf(details.getDurationSeconds())
                    .divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP);
        }
        Instant start = details.getStartTime();
        Instant end = details.getEndTime();
        if (start != null && end != null && end.isAfter(start)) {
            long seconds = end.getEpochSecond() - start.getEpochSecond();
            return BigDecimal.valueOf(seconds)
                    .divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal lookupVmRate(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = VM_HOURLY_RATES.get(nodeType);
        if (rate == null) {
            log.warn("Unknown VM SKU '{}'. Defaulting rate to 0.", nodeType);
            return BigDecimal.ZERO;
        }
        return rate;
    }

    private LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
