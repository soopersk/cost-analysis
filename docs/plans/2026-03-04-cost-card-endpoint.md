# Cost Card Endpoint Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build `GET /api/v1/cost-cards/{calculatorId}` returning the full cost card payload (summary KPIs + trend + daily chart series) for one calculator.

**Architecture:** New `CostCardController` → `CostCardService` → new query methods on the existing `CalculatorRunCostRepository`. A new `calculator_config` table (Flyway migration V4) stores per-calculator `daily_threshold_usd` and `environment`. All DTOs are new; no existing DTOs are modified.

**Tech Stack:** Java 17, Spring Boot 3.5, `NamedParameterJdbcTemplate`, Flyway, Lombok, Jackson.

---

## Assumptions — confirm before implementing

1. `frequency` is a required query param (`DAILY` | `MONTHLY`), consistent with every other endpoint in this codebase.
2. A new `calculator_config` table stores `daily_threshold_usd` and `environment` per calculator. If no row exists for a calculator, `threshold` and `environment` are returned as `null`.
3. **Monthly** = current month-to-date (`DATE_TRUNC('month', CURRENT_DATE)` → today).
4. **Year-to-date** = `Jan 1` of current year → today.
5. **MoM / YoY** trends come from the last two rows of the existing `getMonthlyCostTrend` query (already handles LAG). If insufficient history, direction/value are `null`.
6. **Chart data** = last 90 days of daily `reporting_date` costs for this calculator (re-uses `getCostTrendByCalculator` already in the repo).
7. `navigable` is always `true` — it is a UI hint, not data-driven for now.
8. Currency is always `"USD"`.

---

## Task 1: DB Migration — `calculator_config` table

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__calculator_config.sql`

**Step 1: Write the migration**

```sql
CREATE TABLE IF NOT EXISTS calculator_config (
    calculator_id        VARCHAR(100) PRIMARY KEY,
    environment          VARCHAR(50),
    daily_threshold_usd  NUMERIC(12,4),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**Step 2: Verify Flyway picks it up**

Start the backend (`SPRING_PROFILES_ACTIVE=local mvn spring-boot:run` from `backend/`) and check the log for:
```
Successfully applied 1 migration to schema "public" (V4__calculator_config)
```
Or connect to the DB and confirm the table exists:
```bash
docker exec -it observability-postgres psql -U postgres -d observability -c "\dt calculator_config"
```

**Step 3: Seed one test row**

```sql
INSERT INTO calculator_config (calculator_id, environment, daily_threshold_usd)
VALUES ('calc-abc-123', 'production', 2500.00)
ON CONFLICT DO NOTHING;
```

---

## Task 2: DTOs

**Files:**
- Create: `backend/src/main/java/com/company/observability/dto/CostCardResponse.java`

Put all nested types as `public static` inner classes — avoids file sprawl for types used in exactly one response.

```java
package com.company.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CostCardResponse {

    private String     calculatorId;
    private String     calculatorName;
    private String     environment;
    private String     currency;
    private boolean    navigable;
    private Threshold  threshold;
    private Summary    summary;
    private Chart      chart;

    @Data @Builder
    public static class Threshold {
        private BigDecimal daily;
        private String     label;
        private String     displayValue;
    }

    @Data @Builder
    public static class Summary {
        private PeriodAmount monthly;
        private PeriodAmount yearToDate;
        private Trends       trends;
    }

    @Data @Builder
    public static class PeriodAmount {
        private BigDecimal value;
        private String     displayValue;
        private String     period;
        private String     periodType;
    }

    @Data @Builder
    public static class Trends {
        private TrendItem mom;
        private TrendItem yoy;
    }

    @Data @Builder
    public static class TrendItem {
        private BigDecimal value;
        private String     direction;   // "up" | "down" | null
        private String     displayValue;
    }

    @Data @Builder
    public static class Chart {
        private String         type;
        private String         xAxisLabel;
        private String         yAxisLabel;
        private List<DataPoint> data;
    }

    @Data @Builder
    public static class DataPoint {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate  date;
        private BigDecimal cost;
    }
}
```

**Step 1: Create the file with the code above.**

**Step 2: Verify it compiles**
```bash
cd backend && mvn compile -q
```
Expected: BUILD SUCCESS, no errors.

---

## Task 3: Repository — new query methods

**Files:**
- Modify: `backend/src/main/java/com/company/observability/repository/CalculatorRunCostRepository.java`

Add three new public methods. Add them in a new `// COST CARD` section before the `// UTILITY` section.

**Method 1 — fetch calculator config**

```java
public Optional<Map<String, Object>> getCalculatorConfig(String calculatorId) {
    String sql = """
        SELECT environment, daily_threshold_usd
        FROM calculator_config
        WHERE calculator_id = :calculatorId
        """;
    List<Map<String, Object>> rows = namedJdbc.queryForList(
        sql, Map.of("calculatorId", calculatorId));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
}
```

**Method 2 — month-to-date and year-to-date totals**

```java
public Map<String, BigDecimal> getCostCardTotals(
        String calculatorId, RunFrequency frequency) {

    // language=sql
    String sql = """
        SELECT
            COALESCE(SUM(total_cost_usd) FILTER (
                WHERE reporting_date >= DATE_TRUNC('month', CURRENT_DATE)
            ), 0) AS mtd_cost,
            COALESCE(SUM(total_cost_usd) FILTER (
                WHERE reporting_date >= DATE_TRUNC('year', CURRENT_DATE)
            ), 0) AS ytd_cost
        FROM calculator_run_costs
        WHERE calculator_id = :calculatorId
          AND frequency     = :frequency
          AND reporting_date <= CURRENT_DATE
        """;

    Map<String, Object> params = Map.of(
        "calculatorId", calculatorId,
        "frequency",    frequency.name()
    );

    return namedJdbc.queryForObject(sql, params, (rs, n) -> Map.of(
        "mtd", rs.getBigDecimal("mtd_cost"),
        "ytd", rs.getBigDecimal("ytd_cost")
    ));
}
```

**Method 3 — chart data (last 90 days)**

Re-use the existing `getCostTrendByCalculator` — no new method needed here.
The service will call it with `startDate = today - 90`, `endDate = today`.

**Step 1: Add the two new methods to the repository.**

**Step 2: Compile**
```bash
cd backend && mvn compile -q
```

---

## Task 4: Service

**Files:**
- Create: `backend/src/main/java/com/company/observability/service/CostCardService.java`

```java
package com.company.observability.service;

import com.company.observability.domain.RunFrequency;
import com.company.observability.dto.CostCardResponse;
import com.company.observability.dto.CostCardResponse.*;
import com.company.observability.dto.DailyCostTrend;
import com.company.observability.dto.MonthlyCostTrend;
import com.company.observability.repository.CalculatorRunCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CostCardService {

    private static final String CURRENCY   = "USD";
    private static final int    CHART_DAYS = 90;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMM");

    private final CalculatorRunCostRepository repo;

    public CostCardResponse getCostCard(String calculatorId, RunFrequency frequency) {

        LocalDate today     = LocalDate.now();
        LocalDate chartFrom = today.minusDays(CHART_DAYS);

        // 1. Config (threshold + environment) — may be absent
        Map<String, Object> config = repo.getCalculatorConfig(calculatorId).orElse(Map.of());
        BigDecimal threshold = (BigDecimal) config.get("daily_threshold_usd");
        String     environment = (String) config.get("environment");

        // 2. MTD / YTD totals
        Map<String, BigDecimal> totals = repo.getCostCardTotals(calculatorId, frequency);
        BigDecimal mtd = totals.get("mtd");
        BigDecimal ytd = totals.get("ytd");

        // 3. Trends — last 2 months from monthly trend (LAG gives MoM; last row has YoY if 12+ months exist)
        List<MonthlyCostTrend> trend = repo.getMonthlyCostTrend(calculatorId, frequency, 13);
        MonthlyCostTrend latest = trend.isEmpty() ? null : trend.get(trend.size() - 1);

        // 4. Chart data — last 90 days
        List<DailyCostTrend> dailySeries =
            repo.getCostTrendByCalculator(frequency, chartFrom, today, calculatorId);

        // 5. Calculator name — derive from first chart point's metadata, or fall back to series
        String calcName = trend.isEmpty() ? calculatorId : trend.get(0).getCalculatorName();

        return CostCardResponse.builder()
            .calculatorId(calculatorId)
            .calculatorName(calcName)
            .environment(environment)
            .currency(CURRENCY)
            .navigable(true)
            .threshold(buildThreshold(threshold))
            .summary(buildSummary(mtd, ytd, today, latest))
            .chart(buildChart(dailySeries))
            .build();
    }

    // -------------------------------------------------------------------------

    private Threshold buildThreshold(BigDecimal daily) {
        if (daily == null) return null;
        return Threshold.builder()
            .daily(daily)
            .label("Daily threshold")
            .displayValue(formatAmount(daily))
            .build();
    }

    private Summary buildSummary(
            BigDecimal mtd, BigDecimal ytd, LocalDate today, MonthlyCostTrend latest) {

        String monthLabel = today.format(MONTH_FMT);
        String ytdLabel   = "Jan 1 – " + today.format(DateTimeFormatter.ofPattern("MMM d"));

        return Summary.builder()
            .monthly(PeriodAmount.builder()
                .value(mtd)
                .displayValue(formatAmount(mtd))
                .period(monthLabel)
                .periodType("month_to_date")
                .build())
            .yearToDate(PeriodAmount.builder()
                .value(ytd)
                .displayValue(formatAmount(ytd))
                .period(ytdLabel)
                .periodType("year_to_date")
                .build())
            .trends(buildTrends(latest))
            .build();
    }

    private Trends buildTrends(MonthlyCostTrend latest) {
        if (latest == null) return Trends.builder().build();
        return Trends.builder()
            .mom(toTrendItem(latest.getMomPct()))
            .yoy(toTrendItem(latest.getYoyPct()))
            .build();
    }

    private TrendItem toTrendItem(BigDecimal pct) {
        if (pct == null) return null;
        String direction = pct.compareTo(BigDecimal.ZERO) >= 0 ? "up" : "down";
        BigDecimal abs = pct.abs().setScale(1, RoundingMode.HALF_UP);
        return TrendItem.builder()
            .value(abs)
            .direction(direction)
            .displayValue(abs + "%")
            .build();
    }

    private Chart buildChart(List<DailyCostTrend> series) {
        List<DataPoint> points = series.stream()
            .map(d -> DataPoint.builder()
                .date(d.getDate())
                .cost(d.getTotalCost())
                .build())
            .toList();

        return Chart.builder()
            .type("area")
            .xAxisLabel("Month")
            .yAxisLabel("Cost")
            .data(points)
            .build();
    }

    /** Format a USD value as "$X.Xk" or "$X" depending on magnitude. */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) return null;
        if (amount.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            BigDecimal k = amount.divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
            return "$" + k.stripTrailingZeros().toPlainString() + "k";
        }
        return "$" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
```

**Step 1: Create the file.**

**Step 2: Check `MonthlyCostTrend` has `getMomPct()` and `getYoyPct()` fields.**
Read `backend/src/main/java/com/company/observability/dto/MonthlyCostTrend.java` and confirm field names. Adjust field access in `toTrendItem()` if names differ.

**Step 3: Compile**
```bash
cd backend && mvn compile -q
```

---

## Task 5: Controller

**Files:**
- Create: `backend/src/main/java/com/company/observability/controller/CostCardController.java`

```java
package com.company.observability.controller;

import com.company.observability.domain.RunFrequency;
import com.company.observability.dto.CostCardResponse;
import com.company.observability.service.CostCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Cost card API — one card per calculator.
 *
 * <pre>
 *   GET /api/v1/cost-cards/{calculatorId}?frequency=DAILY
 * </pre>
 *
 * Returns the full cost card payload: identity, threshold, MTD/YTD summary,
 * MoM/YoY trends, and a 90-day daily cost series for the chart.
 */
@RestController
@RequestMapping("/api/v1/cost-cards")
@RequiredArgsConstructor
public class CostCardController {

    private final CostCardService costCardService;

    @GetMapping("/{calculatorId}")
    public ResponseEntity<CostCardResponse> getCostCard(
            @PathVariable                          String       calculatorId,
            @RequestParam(defaultValue = "DAILY")  RunFrequency frequency
    ) {
        return ResponseEntity.ok(costCardService.getCostCard(calculatorId, frequency));
    }
}
```

**Step 1: Create the file.**

**Step 2: Compile and start**
```bash
cd backend && mvn compile -q
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

**Step 3: Smoke test**
```bash
curl -s "http://localhost:8080/api/v1/cost-cards/calc-abc-123?frequency=DAILY" | jq .
```

Expected: JSON with `calculatorId`, `summary.monthly`, `summary.yearToDate`, `summary.trends`, `chart.data` array.

For an unknown calculator with no data, all cost fields should be `0` or `null` — not a 500 error.

**Step 4: Verify in Swagger**

Open `http://localhost:8080/swagger-ui.html` → confirm `GET /api/v1/cost-cards/{calculatorId}` appears.

---

## Task 6: Edge case — calculator not found

After the smoke test, test with a calculator that has no runs at all:

```bash
curl -s "http://localhost:8080/api/v1/cost-cards/nonexistent-calc?frequency=DAILY" | jq .
```

Expected: `200 OK` with zero costs (not 404 or 500). The DB queries use `COALESCE(..., 0)` and `Optional` for config — no NPE. Verify this holds.

If `getMonthlyCostTrend` returns an empty list → `latest = null` → `Trends` with null `mom` and `yoy`. Confirm Jackson serialises nulls as `null` (not omitted). If omitting nulls is preferred, add `@JsonInclude(JsonInclude.Include.NON_NULL)` on `CostCardResponse`.

---

---

## Sample Response

> Data is synthetic but internally consistent: January ≈ $45.1k, February ≈ $63.2k (MoM +40.2%), March MTD = $2,400 (4 days), YTD = $110,650.
> Chart covers 91 days (Dec 4, 2025 – Mar 4, 2026). Weekend costs are visibly lower; Feb spike reflects a higher-cost workload run.
> `momPct` and `yoyPct` come from the existing `getMonthlyCostTrend` LAG query — they compare the last two *complete* calendar months, not MTD.

**Request:**
```
GET /api/v1/cost-cards/calc-rev-attr-001?frequency=DAILY
```

**Response (200 OK):**
```json
{
  "calculatorId": "calc-rev-attr-001",
  "calculatorName": "Revenue Attribution",
  "environment": "production",
  "currency": "USD",
  "navigable": true,
  "threshold": {
    "daily": 2500.00,
    "label": "Daily threshold",
    "displayValue": "$2.5k"
  },
  "summary": {
    "monthly": {
      "value": 2400.00,
      "displayValue": "$2.4k",
      "period": "Mar",
      "periodType": "month_to_date"
    },
    "yearToDate": {
      "value": 110650.00,
      "displayValue": "$110.7k",
      "period": "Jan 1 – Mar 4",
      "periodType": "year_to_date"
    },
    "trends": {
      "mom": {
        "value": 40.2,
        "direction": "up",
        "displayValue": "40.2%"
      },
      "yoy": {
        "value": 10.0,
        "direction": "down",
        "displayValue": "10%"
      }
    }
  },
  "chart": {
    "type": "area",
    "xAxisLabel": "Month",
    "yAxisLabel": "Cost",
    "data": [
      { "date": "2025-12-04", "cost": 1420.00 },
      { "date": "2025-12-05", "cost": 1650.00 },
      { "date": "2025-12-06", "cost": 820.00 },
      { "date": "2025-12-07", "cost": 680.00 },
      { "date": "2025-12-08", "cost": 1580.00 },
      { "date": "2025-12-09", "cost": 1720.00 },
      { "date": "2025-12-10", "cost": 1490.00 },
      { "date": "2025-12-11", "cost": 1640.00 },
      { "date": "2025-12-12", "cost": 1380.00 },
      { "date": "2025-12-13", "cost": 750.00 },
      { "date": "2025-12-14", "cost": 620.00 },
      { "date": "2025-12-15", "cost": 1810.00 },
      { "date": "2025-12-16", "cost": 2140.00 },
      { "date": "2025-12-17", "cost": 1560.00 },
      { "date": "2025-12-18", "cost": 1430.00 },
      { "date": "2025-12-19", "cost": 1680.00 },
      { "date": "2025-12-20", "cost": 790.00 },
      { "date": "2025-12-21", "cost": 650.00 },
      { "date": "2025-12-22", "cost": 1350.00 },
      { "date": "2025-12-23", "cost": 1280.00 },
      { "date": "2025-12-24", "cost": 980.00 },
      { "date": "2025-12-25", "cost": 420.00 },
      { "date": "2025-12-26", "cost": 680.00 },
      { "date": "2025-12-27", "cost": 410.00 },
      { "date": "2025-12-28", "cost": 380.00 },
      { "date": "2025-12-29", "cost": 1420.00 },
      { "date": "2025-12-30", "cost": 1560.00 },
      { "date": "2025-12-31", "cost": 1180.00 },
      { "date": "2026-01-01", "cost": 490.00 },
      { "date": "2026-01-02", "cost": 1320.00 },
      { "date": "2026-01-03", "cost": 780.00 },
      { "date": "2026-01-04", "cost": 650.00 },
      { "date": "2026-01-05", "cost": 1850.00 },
      { "date": "2026-01-06", "cost": 1980.00 },
      { "date": "2026-01-07", "cost": 1590.00 },
      { "date": "2026-01-08", "cost": 1920.00 },
      { "date": "2026-01-09", "cost": 1820.00 },
      { "date": "2026-01-10", "cost": 820.00 },
      { "date": "2026-01-11", "cost": 710.00 },
      { "date": "2026-01-12", "cost": 2080.00 },
      { "date": "2026-01-13", "cost": 1960.00 },
      { "date": "2026-01-14", "cost": 1970.00 },
      { "date": "2026-01-15", "cost": 1620.00 },
      { "date": "2026-01-16", "cost": 1540.00 },
      { "date": "2026-01-17", "cost": 780.00 },
      { "date": "2026-01-18", "cost": 690.00 },
      { "date": "2026-01-19", "cost": 1900.00 },
      { "date": "2026-01-20", "cost": 2020.00 },
      { "date": "2026-01-21", "cost": 1680.00 },
      { "date": "2026-01-22", "cost": 1760.00 },
      { "date": "2026-01-23", "cost": 1960.00 },
      { "date": "2026-01-24", "cost": 820.00 },
      { "date": "2026-01-25", "cost": 710.00 },
      { "date": "2026-01-26", "cost": 1830.00 },
      { "date": "2026-01-27", "cost": 1820.00 },
      { "date": "2026-01-28", "cost": 1760.00 },
      { "date": "2026-01-29", "cost": 1890.00 },
      { "date": "2026-01-30", "cost": 1590.00 },
      { "date": "2026-01-31", "cost": 750.00 },
      { "date": "2026-02-01", "cost": 1380.00 },
      { "date": "2026-02-02", "cost": 2480.00 },
      { "date": "2026-02-03", "cost": 2550.00 },
      { "date": "2026-02-04", "cost": 2540.00 },
      { "date": "2026-02-05", "cost": 2520.00 },
      { "date": "2026-02-06", "cost": 2700.00 },
      { "date": "2026-02-07", "cost": 1420.00 },
      { "date": "2026-02-08", "cost": 1350.00 },
      { "date": "2026-02-09", "cost": 2580.00 },
      { "date": "2026-02-10", "cost": 2640.00 },
      { "date": "2026-02-11", "cost": 2620.00 },
      { "date": "2026-02-12", "cost": 2680.00 },
      { "date": "2026-02-13", "cost": 2760.00 },
      { "date": "2026-02-14", "cost": 1580.00 },
      { "date": "2026-02-15", "cost": 1380.00 },
      { "date": "2026-02-16", "cost": 2650.00 },
      { "date": "2026-02-17", "cost": 2480.00 },
      { "date": "2026-02-18", "cost": 2510.00 },
      { "date": "2026-02-19", "cost": 2590.00 },
      { "date": "2026-02-20", "cost": 2540.00 },
      { "date": "2026-02-21", "cost": 1420.00 },
      { "date": "2026-02-22", "cost": 1380.00 },
      { "date": "2026-02-23", "cost": 2480.00 },
      { "date": "2026-02-24", "cost": 2920.00 },
      { "date": "2026-02-25", "cost": 2560.00 },
      { "date": "2026-02-26", "cost": 2640.00 },
      { "date": "2026-02-27", "cost": 2480.00 },
      { "date": "2026-02-28", "cost": 1360.00 },
      { "date": "2026-03-01", "cost": 540.00 },
      { "date": "2026-03-02", "cost": 640.00 },
      { "date": "2026-03-03", "cost": 620.00 },
      { "date": "2026-03-04", "cost": 600.00 }
    ]
  }
}
```

**Data story visible in the chart:**
- Dec: moderate baseline ($1,200–$1,800 weekdays, dip at Christmas)
- Jan: steady, consistent with Dec baseline
- Feb: elevated across all weekdays ($2,400–$2,900), explaining MoM +40.2%. Feb 24 peaks at $2,920 — just above the $2,500 daily threshold
- Mar 1–4: Sunday low ($540) then Mon–Wed returning to moderate levels

---

## Done

Commit message suggestion:
```
feat: add GET /api/v1/cost-cards/{calculatorId} endpoint

Returns full cost card payload: MTD/YTD summary, MoM/YoY trends,
90-day daily chart series, and per-calculator threshold from new
calculator_config table (V4 migration).
```
