CREATE TABLE IF NOT EXISTS log_events (
    id BIGSERIAL PRIMARY KEY,
    ts TIMESTAMPTZ NOT NULL,
    service VARCHAR(64) NOT NULL,
    level VARCHAR(16) NOT NULL,
    endpoint VARCHAR(128),
    status INTEGER,
    ip VARCHAR(64),
    trace_id VARCHAR(64),
    latency_ms INTEGER,
    message VARCHAR(512)
);

CREATE TABLE IF NOT EXISTS benchmark_runs (
    id BIGSERIAL PRIMARY KEY,
    scenario VARCHAR(64) NOT NULL,
    profile VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    duration_ms BIGINT NOT NULL,
    stats_json TEXT NOT NULL
);
