package com.yassin.datalens.service;

import com.yassin.datalens.dto.ErrorRatePoint;
import com.yassin.datalens.dto.HealthResponse;
import com.yassin.datalens.dto.P95LatencyPoint;
import com.yassin.datalens.dto.SlidingWindowErrorPoint;
import com.yassin.datalens.dto.SuspiciousIpPoint;
import com.yassin.datalens.dto.TopEndpointPoint;
import com.yassin.datalens.dto.TopIpPoint;
import com.yassin.datalens.repository.LogEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AnalyticsService {

    private final JdbcTemplate jdbcTemplate;
    private final LogEventRepository logEventRepository;

    public AnalyticsService(JdbcTemplate jdbcTemplate, LogEventRepository logEventRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.logEventRepository = logEventRepository;
    }

    public HealthResponse health() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return new HealthResponse(true, logEventRepository.count());
    }

    public List<ErrorRatePoint> errorRate(String service, Instant from, Instant to, int bucketMinutes) {
        StringBuilder sql = new StringBuilder("""
                SELECT to_timestamp(floor(extract(epoch from ts)/(? * 60)) * (? * 60)) AS bucket_start,
                       COUNT(*) AS total_count,
                       SUM(CASE WHEN level = 'ERROR' THEN 1 ELSE 0 END) AS error_count,
                       COALESCE(SUM(CASE WHEN level = 'ERROR' THEN 1 ELSE 0 END)::double precision / NULLIF(COUNT(*), 0), 0) AS error_rate
                FROM log_events
                WHERE ts BETWEEN ? AND ?
                """);

        List<Object> params = new ArrayList<>(List.of(bucketMinutes, bucketMinutes, Timestamp.from(from), Timestamp.from(to)));

        if (service != null && !service.isBlank()) {
            sql.append(" AND service = ? ");
            params.add(service.toLowerCase(Locale.ROOT));
        }

        sql.append(" GROUP BY bucket_start ORDER BY bucket_start");

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) ->
                new ErrorRatePoint(
                        rs.getTimestamp("bucket_start").toInstant(),
                        rs.getLong("total_count"),
                        rs.getLong("error_count"),
                        rs.getDouble("error_rate")
                ));
    }

    public List<TopIpPoint> topIps(Instant from, Instant to, int limit) {
        String sql = """
                SELECT ip,
                       COUNT(*) AS request_count,
                       SUM(CASE WHEN level = 'ERROR' OR status IN (401, 403, 429, 500) THEN 1 ELSE 0 END) AS error_like_count
                FROM log_events
                WHERE ts BETWEEN ? AND ?
                  AND ip IS NOT NULL
                GROUP BY ip
                ORDER BY error_like_count DESC, request_count DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setTimestamp(1, Timestamp.from(from));
                    ps.setTimestamp(2, Timestamp.from(to));
                    ps.setInt(3, limit);
                },
                (rs, rowNum) -> new TopIpPoint(
                        rs.getString("ip"),
                        rs.getLong("request_count"),
                        rs.getLong("error_like_count")
                ));
    }

    public List<TopEndpointPoint> topEndpoints(String service, Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT endpoint,
                       COUNT(*) AS request_count,
                       COALESCE(AVG(latency_ms), 0)::double precision AS avg_latency
                FROM log_events
                WHERE ts BETWEEN ? AND ?
                  AND endpoint IS NOT NULL
                """);

        List<Object> params = new ArrayList<>(List.of(Timestamp.from(from), Timestamp.from(to)));
        if (service != null && !service.isBlank()) {
            sql.append(" AND service = ? ");
            params.add(service.toLowerCase(Locale.ROOT));
        }

        sql.append(" GROUP BY endpoint ORDER BY request_count DESC, avg_latency DESC LIMIT ?");
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) ->
                new TopEndpointPoint(rs.getString("endpoint"), rs.getLong("request_count"), rs.getDouble("avg_latency"))
        );
    }

    public List<P95LatencyPoint> p95Latency(String service, Instant from, Instant to, int bucketMinutes) {
        StringBuilder sql = new StringBuilder("""
                SELECT to_timestamp(floor(extract(epoch from ts)/(? * 60)) * (? * 60)) AS bucket_start,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms)::double precision AS p95_latency
                FROM log_events
                WHERE ts BETWEEN ? AND ?
                  AND latency_ms IS NOT NULL
                """);

        List<Object> params = new ArrayList<>(List.of(bucketMinutes, bucketMinutes, Timestamp.from(from), Timestamp.from(to)));
        if (service != null && !service.isBlank()) {
            sql.append(" AND service = ? ");
            params.add(service.toLowerCase(Locale.ROOT));
        }

        sql.append(" GROUP BY bucket_start ORDER BY bucket_start");

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) ->
                new P95LatencyPoint(rs.getTimestamp("bucket_start").toInstant(), rs.getDouble("p95_latency"))
        );
    }

    public List<SuspiciousIpPoint> suspiciousIps(Instant from, Instant to, int limit) {
        String sql = """
                WITH base AS (
                    SELECT ip, ts, status
                    FROM log_events
                    WHERE ts BETWEEN ? AND ?
                      AND ip IS NOT NULL
                ), per_minute AS (
                    SELECT ip,
                           date_trunc('minute', ts) AS minute_bucket,
                           COUNT(*) AS requests_per_minute,
                           SUM(CASE WHEN status IN (401, 403, 429) THEN 1 ELSE 0 END) AS auth_failures_per_minute
                    FROM base
                    GROUP BY ip, minute_bucket
                ), agg AS (
                    SELECT ip,
                           SUM(requests_per_minute) AS total_requests,
                           MAX(requests_per_minute) AS max_requests_per_minute,
                           SUM(auth_failures_per_minute) AS auth_failures
                    FROM per_minute
                    GROUP BY ip
                )
                SELECT ip,
                       total_requests,
                       auth_failures,
                       max_requests_per_minute,
                       ROUND((auth_failures * 2.0 + max_requests_per_minute * 1.5 + total_requests * 0.1)::numeric, 2)::double precision AS suspicion_score,
                       CONCAT('auth_failures=', auth_failures, ', max_rpm=', max_requests_per_minute) AS reasons
                FROM agg
                ORDER BY suspicion_score DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setTimestamp(1, Timestamp.from(from));
                    ps.setTimestamp(2, Timestamp.from(to));
                    ps.setInt(3, limit);
                },
                (rs, rowNum) -> new SuspiciousIpPoint(
                        rs.getString("ip"),
                        rs.getLong("total_requests"),
                        rs.getLong("auth_failures"),
                        rs.getLong("max_requests_per_minute"),
                        rs.getDouble("suspicion_score"),
                        rs.getString("reasons")
                ));
    }

    public List<SlidingWindowErrorPoint> slidingWindowErrors(Instant from, Instant to, int windowMinutes, int stepMinutes) {
        String sql = """
                WITH windows AS (
                    SELECT gs AS window_start,
                           gs + (? * interval '1 minute') AS window_end
                    FROM generate_series(?::timestamptz, ?::timestamptz, (? * interval '1 minute')) gs
                )
                SELECT w.window_start,
                       COUNT(e.id) AS total_count,
                       COALESCE(SUM(CASE WHEN e.level = 'ERROR' THEN 1 ELSE 0 END), 0) AS error_count
                FROM windows w
                LEFT JOIN log_events e
                  ON e.ts >= w.window_start
                 AND e.ts < w.window_end
                GROUP BY w.window_start
                ORDER BY w.window_start
                """;

        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setInt(1, windowMinutes);
                    ps.setTimestamp(2, Timestamp.from(from));
                    ps.setTimestamp(3, Timestamp.from(to));
                    ps.setInt(4, stepMinutes);
                },
                (rs, rowNum) -> new SlidingWindowErrorPoint(
                        rs.getTimestamp("window_start").toInstant(),
                        rs.getLong("total_count"),
                        rs.getLong("error_count")
                ));
    }
}
