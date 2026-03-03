TRUNCATE TABLE benchmark_runs RESTART IDENTITY;
TRUNCATE TABLE log_events RESTART IDENTITY;

INSERT INTO log_events (ts, service, level, endpoint, status, ip, trace_id, latency_ms, message) VALUES
('2026-01-01T00:00:00Z','api','ERROR','/api/login',401,'10.0.0.1','t1',100,'fixture-1'),
('2026-01-01T00:00:10Z','api','WARN','/api/login',403,'10.0.0.1','t2',200,'fixture-2'),
('2026-01-01T00:00:20Z','api','WARN','/api/login',429,'10.0.0.1','t3',300,'fixture-3'),
('2026-01-01T00:01:00Z','api','WARN','/api/orders',401,'10.0.0.1','t4',50,'fixture-4'),
('2026-01-01T00:01:30Z','api','INFO','/api/orders',200,'10.0.0.1','t5',70,'fixture-5'),
('2026-01-01T00:05:00Z','api','ERROR','/api/search',500,'10.0.0.1','t6',500,'fixture-6'),
('2026-01-01T00:05:30Z','api','INFO','/api/search',200,'10.0.0.2','t7',500,'fixture-7'),
('2026-01-01T00:06:00Z','api','INFO','/api/search',200,'10.0.0.2','t8',500,'fixture-8'),
('2026-01-01T00:06:30Z','api','INFO','/api/login',200,'10.0.0.2','t9',400,'fixture-9'),
('2026-01-01T00:07:00Z','api','INFO','/api/orders',200,'10.0.0.3','t10',90,'fixture-10');
