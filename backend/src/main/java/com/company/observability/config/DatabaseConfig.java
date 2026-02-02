package com.company.observability.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Database configuration for JdbcTemplate
 */
@Configuration
@EnableTransactionManagement
public class DatabaseConfig {

    /**
     * JdbcTemplate bean for standard JDBC operations
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // Configure fetch size for large result sets
        jdbcTemplate.setFetchSize(1000);

        // Configure max rows (safety limit)
        jdbcTemplate.setMaxRows(100000);

        // Configure query timeout (30 seconds)
        jdbcTemplate.setQueryTimeout(30);

        return jdbcTemplate;
    }

    /**
     * NamedParameterJdbcTemplate for named parameter queries
     */
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * Transaction manager for declarative transaction management
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}