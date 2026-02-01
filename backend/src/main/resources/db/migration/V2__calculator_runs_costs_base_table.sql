CREATE TABLE IF NOT EXISTS calculator_run_costs (
    cost_id BIGSERIAL,

    calculator_run_id VARCHAR(100) NOT NULL,
    reporting_date DATE NOT NULL,

    calculator_id VARCHAR(100) NOT NULL,
    calculator_name VARCHAR(255) NOT NULL,

    databricks_run_id BIGINT NOT NULL,
    cluster_id VARCHAR(100) NOT NULL,

    -- Cluster configuration
    driver_node_type VARCHAR(50),
    worker_node_type VARCHAR(50),
    min_workers INTEGER,
    max_workers INTEGER,
    avg_worker_count NUMERIC(6,2),
    peak_worker_count INTEGER,
    spot_instance_used BOOLEAN,
    photon_enabled BOOLEAN,

    -- Runtime metrics
    run_start_time TIMESTAMP NOT NULL,
    run_end_time TIMESTAMP NOT NULL,
    duration_seconds INTEGER NOT NULL,
    run_status VARCHAR(20) NOT NULL,
    is_retry BOOLEAN,
    attempt_number INTEGER,

    -- DBU costs
    dbu_units_consumed NUMERIC(12,4),
    dbu_unit_price NUMERIC(8,4),
    dbu_cost_usd NUMERIC(12,4),
    dbu_sku VARCHAR(100),

    -- VM costs
    vm_driver_cost_usd NUMERIC(12,4),
    vm_worker_cost_usd NUMERIC(12,4),
    vm_total_cost_usd NUMERIC(12,4),
    vm_node_hours NUMERIC(12,2),

    -- Storage costs
    storage_input_gb NUMERIC(12,2),
    storage_output_gb NUMERIC(12,2),
    storage_temp_gb NUMERIC(12,2),
    storage_cost_usd NUMERIC(12,4),

    -- Network costs
    network_egress_gb NUMERIC(12,2),
    network_cost_usd NUMERIC(12,4),

    -- Total cost
    total_cost_usd NUMERIC(12,4),

    -- Allocation metadata
    allocation_method VARCHAR(50) NOT NULL,
    confidence_score NUMERIC(5,4) NOT NULL,
    concurrent_runs_on_cluster INTEGER,
    cluster_utilization_pct NUMERIC(5,2),

    -- Data lineage
    calculation_version VARCHAR(20) NOT NULL,
    calculation_timestamp TIMESTAMP NOT NULL,
    calculated_by VARCHAR(100),

    -- Manual adjustments
    manual_adjustment_usd NUMERIC(12,4),
    manual_adjustment_reason TEXT,
    adjusted_by VARCHAR(100),
    adjusted_at TIMESTAMP,

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Composite Primary Key
    PRIMARY KEY (calculator_run_id, reporting_date)
);
