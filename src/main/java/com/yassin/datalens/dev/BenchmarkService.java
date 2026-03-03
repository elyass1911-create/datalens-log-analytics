package com.yassin.datalens.dev;

import com.yassin.datalens.dto.BenchmarkStatsResponse;
import com.yassin.datalens.model.BenchmarkRun;
import com.yassin.datalens.repository.BenchmarkRunRepository;
import com.yassin.datalens.service.TimeRange;
import com.yassin.datalens.service.TimeRangeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BenchmarkService {

    private final JdbcTemplate jdbcTemplate;
    private final IndexManagementService indexManagementService;
    private final BenchmarkRunRepository benchmarkRunRepository;
    private final TimeRangeResolver timeRangeResolver;

    public BenchmarkService(JdbcTemplate jdbcTemplate,
                            IndexManagementService indexManagementService,
                            BenchmarkRunRepository benchmarkRunRepository,
                            TimeRangeResolver timeRangeResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.indexManagementService = indexManagementService;
        this.benchmarkRunRepository = benchmarkRunRepository;
        this.timeRangeResolver = timeRangeResolver;
    }

    public BenchmarkStatsResponse run(BenchmarkScenario scenario, int iterations) {
        if (iterations < 1 || iterations > 50) {
            throw new IllegalArgumentException("iterations must be between 1 and 50");
        }

        TimeRange range = timeRangeResolver.resolve(null, null, 24);
        Instant startedAt = Instant.now();
        long startedNs = System.nanoTime();

        Map<String, List<Double>> measurements = new LinkedHashMap<>();
        for (String variant : variantsForScenario(scenario)) {
            measurements.put(variant, new ArrayList<>());
            for (int i = 0; i < iterations; i++) {
                long t0 = System.nanoTime();
                executeVariant(scenario, variant, range);
                long elapsed = System.nanoTime() - t0;
                measurements.get(variant).add(elapsed / 1_000_000.0);
            }
        }

        long totalDurationMs = (System.nanoTime() - startedNs) / 1_000_000;
        List<BenchmarkStatsResponse.VariantStat> stats = measurements.entrySet().stream()
                .map(e -> toStats(e.getKey(), e.getValue()))
                .toList();

        BenchmarkStatsResponse response = new BenchmarkStatsResponse(
                scenario.apiName(),
                indexManagementService.detectActiveProfile().name().toLowerCase(),
                iterations,
                stats,
                totalDurationMs
        );

        persistRun(scenario, response, startedAt, totalDurationMs);
        return response;
    }

    private void persistRun(BenchmarkScenario scenario, BenchmarkStatsResponse response, Instant startedAt, long durationMs) {
        BenchmarkRun run = new BenchmarkRun();
        run.setScenario(scenario.apiName());
        run.setProfile(response.profile());
        run.setStartedAt(startedAt);
        run.setDurationMs(durationMs);
        run.setStatsJson(toJson(response));
        benchmarkRunRepository.save(run);
    }

    private String toJson(BenchmarkStatsResponse response) {
        String variants = response.variants().stream()
                .map(v -> "{\"variant\":\"" + v.variant() + "\",\"meanMs\":" + v.meanMs()
                        + ",\"p95Ms\":" + v.p95Ms() + ",\"minMs\":" + v.minMs() + ",\"maxMs\":" + v.maxMs() + "}")
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        return "{\"scenario\":\"" + response.scenario() + "\",\"profile\":\"" + response.profile()
                + "\",\"iterations\":" + response.iterations() + ",\"variants\":[" + variants
                + "],\"totalDurationMs\":" + response.totalDurationMs() + "}";
    }

    private List<String> variantsForScenario(BenchmarkScenario scenario) {
        return switch (scenario) {
            case ERROR_RATE -> List.of("date_trunc_bucket", "generated_series_bucket");
            case TOP_IPS -> List.of("base");
            case P95_LATENCY -> List.of("base");
        };
    }

    private void executeVariant(BenchmarkScenario scenario, String variant, TimeRange range) {
        switch (scenario) {
            case ERROR_RATE -> {
                if ("generated_series_bucket".equals(variant)) {
                    jdbcTemplate.queryForList(errorRateGeneratedSeriesSql(), Timestamp.from(range.from()), Timestamp.from(range.to()));
                } else {
                    jdbcTemplate.queryForList(errorRateDateTruncSql(), Timestamp.from(range.from()), Timestamp.from(range.to()));
                }
            }
            case TOP_IPS -> jdbcTemplate.queryForList(topIpsSql(), Timestamp.from(range.from()), Timestamp.from(range.to()), 20);
            case P95_LATENCY -> jdbcTemplate.queryForList(p95LatencySql(), Timestamp.from(range.from()), Timestamp.from(range.to()));
        }
    }

    public String explain(BenchmarkScenario scenario, String variant) {
        TimeRange range = timeRangeResolver.resolve(null, null, 24);
        List<String> rows;
        switch (scenario) {
            case ERROR_RATE -> {
                String innerSql = "generated_series_bucket".equalsIgnoreCase(variant)
                        ? errorRateGeneratedSeriesSql()
                        : errorRateDateTruncSql();
                rows = explainSql(innerSql, Timestamp.from(range.from()), Timestamp.from(range.to()));
            }
            case TOP_IPS -> rows = explainSql(topIpsSql(), Timestamp.from(range.from()), Timestamp.from(range.to()), 20);
            case P95_LATENCY -> rows = explainSql(p95LatencySql(), Timestamp.from(range.from()), Timestamp.from(range.to()));
            default -> throw new IllegalArgumentException("Unsupported scenario");
        }
        return String.join("\n", rows);
    }

    private List<String> explainSql(String sql, Object... args) {
        return jdbcTemplate.query("EXPLAIN ANALYZE " + sql, args, (rs, rowNum) -> rs.getString(1));
    }

    private BenchmarkStatsResponse.VariantStat toStats(String name, List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / values.size();
        double min = sorted.getFirst();
        double max = sorted.getLast();
        int p95Index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        double p95 = sorted.get(Math.max(0, p95Index));
        return new BenchmarkStatsResponse.VariantStat(name, round(mean), round(p95), round(min), round(max));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String errorRateDateTruncSql() {
        return """
                SELECT to_timestamp(floor(extract(epoch from ts)/(5 * 60)) * (5 * 60)) AS bucket_start,
                       COUNT(*) AS total_count,
                       SUM(CASE WHEN level = 'ERROR' THEN 1 ELSE 0 END) AS error_count
                FROM log_events
                WHERE ts BETWEEN ? AND ?
                GROUP BY bucket_start
                ORDER BY bucket_start
                """;
    }

    private String errorRateGeneratedSeriesSql() {
        return """
                WITH buckets AS (
                    SELECT gs AS bucket_start,
                           gs + interval '5 minute' AS bucket_end
                    FROM generate_series(?::timestamptz, ?::timestamptz, interval '5 minute') gs
                )
                SELECT b.bucket_start,
                       COUNT(e.id) AS total_count,
                       COALESCE(SUM(CASE WHEN e.level = 'ERROR' THEN 1 ELSE 0 END), 0) AS error_count
                FROM buckets b
                LEFT JOIN log_events e
                  ON e.ts >= b.bucket_start
                 AND e.ts < b.bucket_end
                GROUP BY b.bucket_start
                ORDER BY b.bucket_start
                """;
    }

    private String topIpsSql() {
        return """
                SELECT ip,
                       COUNT(*) AS request_count,
                       SUM(CASE WHEN level = 'ERROR' OR status IN (401, 403, 429, 500) THEN 1 ELSE 0 END) AS error_like_count
                FROM log_events
                WHERE ts BETWEEN ? AND ?
                GROUP BY ip
                ORDER BY error_like_count DESC, request_count DESC
                LIMIT ?
                """;
    }

    private String p95LatencySql() {
        return """
                SELECT to_timestamp(floor(extract(epoch from ts)/(5 * 60)) * (5 * 60)) AS bucket_start,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms)::double precision AS p95_latency
                FROM log_events
                WHERE ts BETWEEN ? AND ?
                  AND latency_ms IS NOT NULL
                GROUP BY bucket_start
                ORDER BY bucket_start
                """;
    }
}
