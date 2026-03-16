package com.company.observability.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "cost-analytics")
public class CostAnalyticsProperties {
    private List<String> excludedCalculators = List.of();
}
