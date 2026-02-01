-- ---------------------------------------------------
-- Seed sample calculator run cost data
-- ---------------------------------------------------

INSERT INTO calculator_run_costs (
    calculator_run_id,
    reporting_date,
    calculator_id,
    calculator_name,
    databricks_run_id,
    cluster_id,

    driver_node_type,
    worker_node_type,
    min_workers,
    max_workers,
    avg_worker_count,
    peak_worker_count,
    spot_instance_used,
    photon_enabled,

    run_start_time,
    run_end_time,
    duration_seconds,
    run_status,
    is_retry,
    attempt_number,

    dbu_units_consumed,
    dbu_unit_price,
    dbu_cost_usd,
    dbu_sku,

    vm_driver_cost_usd,
    vm_worker_cost_usd,
    vm_total_cost_usd,
    vm_node_hours,

    storage_input_gb,
    storage_output_gb,
    storage_temp_gb,
    storage_cost_usd,

    network_egress_gb,
    network_cost_usd,

    total_cost_usd,

    allocation_method,
    confidence_score,
    concurrent_runs_on_cluster,
    cluster_utilization_pct,

    calculation_version,
    calculation_timestamp,
    calculated_by
)
SELECT
    -- IDs
    'run_' || g.run_id,
    g.run_date,
    g.calculator_id,
    g.calculator_name,
    100000 + g.run_id,
    'cluster_' || (g.run_id % 5),

    -- Cluster config
    'm5.xlarge',
    'm5.2xlarge',
    2,
    10,
    (2 + (random() * 6))::numeric(6,2),
    (4 + (random() * 8))::int,
    g.spot_used,
    g.photon_enabled,

    -- Runtime
    g.start_time,
    g.start_time + (g.duration_seconds || ' seconds')::interval,
    g.duration_seconds,
    g.run_status,
    g.is_retry,
    g.attempt_number,

    -- DBU
    g.duration_seconds / 3600.0 * 5,
    0.55,
    g.duration_seconds / 3600.0 * 5 * 0.55,
    'DBU-PREMIUM',

    -- VM
    1.20,
    g.duration_seconds / 3600.0 * 3.5,
    1.20 + (g.duration_seconds / 3600.0 * 3.5),
    g.duration_seconds / 3600.0,

    -- Storage
    10 + random() * 50,
    5 + random() * 20,
    2 + random() * 10,
    0.15 + random() * 0.4,

    -- Network
    1 + random() * 5,
    0.05 + random() * 0.15,

    -- Total
    (g.duration_seconds / 3600.0 * 5 * 0.55)
      + (1.20 + (g.duration_seconds / 3600.0 * 3.5))
      + (0.15 + random() * 0.4)
      + (0.05 + random() * 0.15),

    -- Allocation
    'PROPORTIONAL_RUNTIME',
    round((0.85 + random() * 0.14)::numeric, 4),
    (1 + random() * 3)::int,
    round((55 + random() * 35)::numeric, 2),

    -- Lineage
    'v1.0',
    now(),
    'system'
FROM (
    SELECT
        row_number() OVER () AS run_id,
        d::date AS run_date,
        c.calculator_id,
        c.calculator_name,

        (d + (random() * interval '20 hours')) AS start_time,
        (300 + random() * 5400)::int AS duration_seconds,

        CASE
            WHEN random() < 0.88 THEN 'SUCCESS'
            WHEN random() < 0.95 THEN 'FAILED'
            ELSE 'SUCCESS'
        END AS run_status,

        (random() < 0.08) AS is_retry,
        CASE WHEN random() < 0.08 THEN 2 ELSE 1 END AS attempt_number,

        (random() < 0.65) AS spot_used,
        (random() < 0.45) AS photon_enabled

    FROM generate_series(
            current_date - interval '29 days',
            current_date,
            interval '1 day'
         ) d
    CROSS JOIN (
        VALUES
            ('DQ',   'DataQualityCheck'),
            ('RS',   'RiskScoring'),
            ('REV',  'RevenueAggregation'),
            ('SEG',  'CustomerSegmentation'),
            ('FRD',  'FraudDetection')
    ) c(calculator_id, calculator_name)
) g;
