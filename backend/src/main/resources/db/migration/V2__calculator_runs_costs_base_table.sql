CREATE TABLE IF NOT EXISTS calculator_run_costs (
    cost_id BIGSERIAL,

    run_id BIGINT NOT NULL,
    reporting_date DATE NOT NULL,
    frequency VARCHAR(20) NOT NULL DEFAULT 'DAILY',

    calculator_id VARCHAR(100) NOT NULL,
    calculator_name VARCHAR(255) NOT NULL,

    databricks_run_id BIGINT,
    cluster_id VARCHAR(100),
    job_id BIGINT,
    job_name VARCHAR(255),

    -- Cluster configuration
    driver_node_type VARCHAR(50),
    worker_node_type VARCHAR(50),
    worker_count INTEGER,
    min_workers INTEGER,
    max_workers INTEGER,
    avg_worker_count NUMERIC(6,2),
    peak_worker_count INTEGER,
    spot_instances BOOLEAN,
    photon_enabled BOOLEAN,
        
    spark_version VARCHAR(50),
    runtime_engine VARCHAR(20),
    cluster_source VARCHAR(20),
    workload_type VARCHAR(50),
    region VARCHAR(50),
    autoscale_enabled BOOLEAN,
    autoscale_min INTEGER,
    autoscale_max INTEGER,

    -- Runtime metrics
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    duration_seconds INTEGER NOT NULL,
    duration_hours NUMERIC(10,4),
    status VARCHAR(20) NOT NULL,
    is_retry BOOLEAN,
    attempt_number INTEGER,

    -- DBU costs
    dbu_cost_usd NUMERIC(12,4),

    -- VM costs
    vm_cost_usd NUMERIC(12,4),

    -- Storage costs
    storage_cost_usd NUMERIC(12,4),

    -- Total cost
    total_cost_usd NUMERIC(12,4),

    -- Cost calculation lifecycle
    cost_calculation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cost_calculated_at      TIMESTAMP,
    cost_calculation_notes  TEXT,

    -- Allocation metadata
    allocation_method VARCHAR(50),
    confidence_score NUMERIC(5,4),
    concurrent_runs_on_cluster INTEGER,
    cluster_utilization_pct NUMERIC(5,2),

    -- Data lineage
    calculation_version VARCHAR(20),
    calculation_timestamp TIMESTAMP,
    calculated_by VARCHAR(100),

    -- Manual adjustments
    manual_adjustment_usd NUMERIC(12,4),
    manual_adjustment_reason TEXT,
    adjusted_by VARCHAR(100),
    adjusted_at TIMESTAMP,

    task_version VARCHAR(100),
    context_id VARCHAR(100),
    megdp_run_id VARCHAR(100),
    tenant_abb VARCHAR(50),
    collection_timestamp TIMESTAMP,


    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Composite Primary Key
    PRIMARY KEY (run_id, reporting_date)
);
