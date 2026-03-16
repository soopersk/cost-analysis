package com.company.observability.service;

import com.company.observability.config.CostAnalyticsProperties;
import com.company.observability.domain.RunFrequency;
import com.company.observability.dto.*;
import com.company.observability.repository.CalculatorRunCostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CostAnalyticsServiceExclusionTest {

    private CalculatorRunCostRepository repo;

    @BeforeEach
    void setUp() {
        repo = mock(CalculatorRunCostRepository.class);
    }

    private CostAnalyticsService serviceWithPatterns(String... patterns) {
        CostAnalyticsProperties props = new CostAnalyticsProperties();
        props.setExcludedCalculators(List.of(patterns));
        return new CostAnalyticsService(repo, props);
    }

    @Test
    void exact_match_excludes_calculator() {
        when(repo.getCalculatorCostSummary(any(), any(), any()))
                .thenReturn(List.of(summary("test_calculator"), summary("prod_calc")));

        var service = serviceWithPatterns("test_calculator");
        var result = service.getCalculatorCostSummary(RunFrequency.DAILY, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).extracting(CalculatorCostSummary::getCalculatorId)
                .containsExactly("prod_calc");
    }

    @Test
    void suffix_wildcard_excludes_matching_calculators() {
        when(repo.getCalculatorCostSummary(any(), any(), any()))
                .thenReturn(List.of(summary("data_curation"), summary("prod_curation"), summary("prod_calc")));

        var service = serviceWithPatterns("*curation");
        var result = service.getCalculatorCostSummary(RunFrequency.DAILY, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).extracting(CalculatorCostSummary::getCalculatorId)
                .containsExactly("prod_calc");
    }

    @Test
    void prefix_wildcard_excludes_matching_calculators() {
        when(repo.getCalculatorCostSummary(any(), any(), any()))
                .thenReturn(List.of(summary("staging_alpha"), summary("staging_beta"), summary("prod_calc")));

        var service = serviceWithPatterns("staging_*");
        var result = service.getCalculatorCostSummary(RunFrequency.DAILY, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).extracting(CalculatorCostSummary::getCalculatorId)
                .containsExactly("prod_calc");
    }

    @Test
    void contains_wildcard_excludes_matching_calculators() {
        when(repo.getCalculatorCostSummary(any(), any(), any()))
                .thenReturn(List.of(summary("my_test_calc"), summary("prod_calc")));

        var service = serviceWithPatterns("*test*");
        var result = service.getCalculatorCostSummary(RunFrequency.DAILY, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).extracting(CalculatorCostSummary::getCalculatorId)
                .containsExactly("prod_calc");
    }

    @Test
    void matching_is_case_insensitive() {
        when(repo.getCalculatorCostSummary(any(), any(), any()))
                .thenReturn(List.of(summary("Test_Calculator"), summary("prod_calc")));

        var service = serviceWithPatterns("test_calculator");
        var result = service.getCalculatorCostSummary(RunFrequency.DAILY, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).extracting(CalculatorCostSummary::getCalculatorId)
                .containsExactly("prod_calc");
    }

    @Test
    void empty_exclusion_list_returns_all() {
        when(repo.getCalculatorCostSummary(any(), any(), any()))
                .thenReturn(List.of(summary("test_calculator"), summary("prod_calc")));

        var service = serviceWithPatterns();
        var result = service.getCalculatorCostSummary(RunFrequency.DAILY, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).hasSize(2);
    }

    @Test
    void multiple_patterns_union_of_exclusions() {
        when(repo.getCalculatorCostSummary(any(), any(), any()))
                .thenReturn(List.of(summary("test_calc"), summary("data_curation"), summary("prod_calc")));

        var service = serviceWithPatterns("test_calc", "*curation");
        var result = service.getCalculatorCostSummary(RunFrequency.DAILY, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).extracting(CalculatorCostSummary::getCalculatorId)
                .containsExactly("prod_calc");
    }

    @Test
    void recent_runs_excludes_matching_calculators() {
        when(repo.getRecentRunsWithCost(anyInt(), isNull(), any(), isNull()))
                .thenReturn(List.of(recentRun("test_calc"), recentRun("prod_calc")));

        var service = serviceWithPatterns("test_calc");
        var result = service.getRecentRuns(RunFrequency.DAILY, 20, null, null);

        assertThat(result).extracting(RecentRunCost::getCalculatorId)
                .containsExactly("prod_calc");
    }

    @Test
    void anomalies_excludes_matching_calculators() {
        when(repo.getCostAnomalies(any(), anyDouble(), any(), any()))
                .thenReturn(List.of(anomaly("test_calc"), anomaly("prod_calc")));

        var service = serviceWithPatterns("test_calc");
        var result = service.getCostAnomalies(RunFrequency.DAILY, 3.0, LocalDate.now().minusDays(30), LocalDate.now());

        assertThat(result).extracting(CostAnomaly::getCalculatorId)
                .containsExactly("prod_calc");
    }

    @Test
    void star_alone_excludes_all_calculators() {
        when(repo.getCalculatorCostSummary(any(), any(), any()))
                .thenReturn(List.of(summary("calc_a"), summary("calc_b")));

        var service = serviceWithPatterns("*");
        var result = service.getCalculatorCostSummary(RunFrequency.DAILY, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).isEmpty();
    }

    @Test
    void null_calculator_id_in_result_is_not_excluded() {
        CalculatorCostSummary nullIdSummary = new CalculatorCostSummary();
        nullIdSummary.setCalculatorId(null);
        nullIdSummary.setCalculatorName("unknown");
        nullIdSummary.setFrequency(RunFrequency.DAILY);
        nullIdSummary.setTotalRuns(1L);
        nullIdSummary.setTotalCost(BigDecimal.ONE);
        nullIdSummary.setAvgCostPerRun(BigDecimal.ONE);
        nullIdSummary.setDbuCost(BigDecimal.ZERO);
        nullIdSummary.setVmCost(BigDecimal.ZERO);
        nullIdSummary.setStorageCost(BigDecimal.ZERO);
        nullIdSummary.setSuccessfulRuns(1L);
        nullIdSummary.setFailedRuns(0L);
        nullIdSummary.setSpotInstanceRuns(0L);
        nullIdSummary.setPhotonEnabledRuns(0L);

        when(repo.getCalculatorCostSummary(any(), any(), any()))
                .thenReturn(List.of(nullIdSummary, summary("prod_calc")));

        var service = serviceWithPatterns("test_calc");
        var result = service.getCalculatorCostSummary(RunFrequency.DAILY, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).hasSize(2);
    }

    @Test
    void top_expensive_excludes_matching_calculators() {
        when(repo.getCalculatorCostSummary(any(), any(), any()))
                .thenReturn(List.of(summary("test_calc"), summary("prod_calc_1"), summary("prod_calc_2")));

        var service = serviceWithPatterns("test_calc");
        var result = service.getTopExpensiveCalculators(RunFrequency.DAILY, 10, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).extracting(CalculatorCostSummary::getCalculatorId)
                .doesNotContain("test_calc");
    }

    private CalculatorCostSummary summary(String calculatorId) {
        CalculatorCostSummary s = new CalculatorCostSummary();
        s.setCalculatorId(calculatorId);
        s.setCalculatorName(calculatorId + "_name");
        s.setFrequency(RunFrequency.DAILY);
        s.setTotalRuns(10L);
        s.setTotalCost(BigDecimal.TEN);
        s.setAvgCostPerRun(BigDecimal.ONE);
        s.setDbuCost(BigDecimal.ZERO);
        s.setVmCost(BigDecimal.ZERO);
        s.setStorageCost(BigDecimal.ZERO);
        s.setSuccessfulRuns(10L);
        s.setFailedRuns(0L);
        s.setSpotInstanceRuns(0L);
        s.setPhotonEnabledRuns(0L);
        return s;
    }

    private RecentRunCost recentRun(String calculatorId) {
        RecentRunCost r = new RecentRunCost();
        r.setCalculatorId(calculatorId);
        r.setRunId(1L);
        r.setFrequency(RunFrequency.DAILY);
        r.setStatus("SUCCESS");
        r.setDurationSeconds(300);
        r.setTotalCost(BigDecimal.TEN);
        return r;
    }

    private CostAnomaly anomaly(String calculatorId) {
        CostAnomaly a = new CostAnomaly();
        a.setCalculatorId(calculatorId);
        a.setRunId(1L);
        a.setFrequency(RunFrequency.DAILY);
        a.setActualCost(BigDecimal.TEN);
        a.setAverageCost(BigDecimal.ONE);
        a.setCostMultiplier(BigDecimal.TEN);
        return a;
    }
}
