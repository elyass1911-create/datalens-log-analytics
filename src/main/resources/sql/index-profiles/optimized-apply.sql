CREATE INDEX IF NOT EXISTS idx_log_event_level_ts ON log_events (level, ts);
CREATE INDEX IF NOT EXISTS idx_log_event_service_ts ON log_events (service, ts);
CREATE INDEX IF NOT EXISTS idx_log_event_ip_ts ON log_events (ip, ts);
CREATE INDEX IF NOT EXISTS idx_log_event_endpoint_ts ON log_events (endpoint, ts);
CREATE INDEX IF NOT EXISTS idx_log_event_status_ts ON log_events (status, ts);
CREATE INDEX IF NOT EXISTS idx_log_event_service_level_ts ON log_events (service, level, ts);
