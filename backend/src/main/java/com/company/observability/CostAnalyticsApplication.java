package com.company.observability;

import com.company.observability.config.CostAnalyticsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CostAnalyticsProperties.class)
public class CostAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CostAnalyticsApplication.class, args);
    }
}