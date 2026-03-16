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

    private String    calculatorId;
    private String    calculatorName;
    private String    environment;
    private String    currency;
    private boolean   navigable;
    private Threshold threshold;
    private Summary   summary;
    private Chart     chart;

    @Data @Builder
    public static class Threshold {
        private BigDecimal daily;
        private String     label;
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
        private BigDecimal value;   // signed: positive = up, negative = down
    }

    @Data @Builder
    public static class Chart {
        private String          type;
        private String          xAxisLabel;
        private String          yAxisLabel;
        private List<DataPoint> data;
    }

    @Data @Builder
    public static class DataPoint {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate  date;
        private BigDecimal cost;
    }
}
