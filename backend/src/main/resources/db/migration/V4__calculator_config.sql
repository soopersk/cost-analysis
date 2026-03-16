CREATE TABLE IF NOT EXISTS calculator_config (
    calculator_id        VARCHAR(100) PRIMARY KEY,
    environment          VARCHAR(50),
    daily_threshold_usd  NUMERIC(12,4),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
