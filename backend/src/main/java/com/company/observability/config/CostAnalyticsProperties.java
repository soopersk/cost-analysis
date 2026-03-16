package com.company.observability.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "cost-analytics")
public class CostAnalyticsProperties {
    private List<String> excludedCalculators = new ArrayList<>();
}
