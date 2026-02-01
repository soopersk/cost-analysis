-- flyway:transactional=false

CREATE INDEX IF NOT EXISTS ix_crc_reporting_date
    ON calculator_run_costs (reporting_date);

-- Date + calculator filtering
CREATE INDEX IF NOT EXISTS ix_crc_reporting_date_calculator
    ON calculator_run_costs (reporting_date, calculator_id);

-- Recent runs ordering
CREATE INDEX IF NOT EXISTS ix_crc_run_start_time_desc
    ON calculator_run_costs (run_start_time DESC);

-- Recent runs per calculator
CREATE INDEX IF NOT EXISTS ix_crc_calculator_run_start_time_desc
    ON calculator_run_costs (calculator_id, run_start_time DESC);

-- Status-based filtering
CREATE INDEX IF NOT EXISTS ix_crc_run_status
    ON calculator_run_costs (run_status);

-- Partial index for anomaly detection (SUCCESS only)
CREATE INDEX IF NOT EXISTS ix_crc_success_runs
    ON calculator_run_costs (calculator_id, total_cost_usd)
    WHERE run_status = 'SUCCESS';

-- Covering index for heavy aggregations
CREATE INDEX IF NOT EXISTS ix_crc_reporting_date_costs
    ON calculator_run_costs (
        reporting_date,
        total_cost_usd,
        dbu_cost_usd,
        vm_total_cost_usd,
        storage_cost_usd,
        network_cost_usd
    );

