-- flyway:transactional=false

CREATE INDEX IF NOT EXISTS ix_crc_reporting_date
    ON calculator_run_costs (reporting_date);

-- Date + calculator filtering
CREATE INDEX IF NOT EXISTS ix_crc_reporting_date_calculator
    ON calculator_run_costs (reporting_date, calculator_id);

-- Recent runs ordering
CREATE INDEX IF NOT EXISTS ix_crc_run_start_time_desc
    ON calculator_run_costs (start_time DESC);

-- Recent runs per calculator
CREATE INDEX IF NOT EXISTS ix_crc_calculator_run_start_time_desc
    ON calculator_run_costs (calculator_id, start_time DESC);


-- Partial index for anomaly detection (SUCCESS only)
CREATE INDEX IF NOT EXISTS ix_crc_success_runs
    ON calculator_run_costs (calculator_id, total_cost_usd)
    WHERE status = 'SUCCESS';

-- Covering index for heavy aggregations
CREATE INDEX IF NOT EXISTS ix_crc_reporting_date_costs
    ON calculator_run_costs (
        reporting_date,
        total_cost_usd,
        dbu_cost_usd,
        vm_cost_usd,
        storage_cost_usd
    );


-- Cost calculation work-queue scan
CREATE INDEX IF NOT EXISTS ix_crc_cost_calculation_status
    ON calculator_run_costs (cost_calculation_status)
    WHERE cost_calculation_status = 'PENDING';

-- All analytics queries filter WHERE frequency = ?  (DAILY | MONTHLY)
CREATE INDEX IF NOT EXISTS ix_crc_frequency
    ON calculator_run_costs (frequency);

-- Compound index for the most common access pattern:
-- date-range scan scoped to a single frequency
CREATE INDEX IF NOT EXISTS ix_crc_frequency_date
    ON calculator_run_costs (frequency, reporting_date);