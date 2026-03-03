package com.yassin.datalens.service;

import com.yassin.datalens.dto.SeedResponse;
import com.yassin.datalens.model.LogEvent;
import com.yassin.datalens.repository.LogEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Service
public class EventIngestionService {

    private static final int CHUNK_SIZE = 5_000;

    private final SyntheticLogEventGenerator generator;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final LogEventRepository logEventRepository;

    public EventIngestionService(SyntheticLogEventGenerator generator,
                                 JdbcTemplate jdbcTemplate,
                                 EntityManager entityManager,
                                 LogEventRepository logEventRepository) {
        this.generator = generator;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.logEventRepository = logEventRepository;
    }

    public SeedResponse seed(long requested, String mode, int days) {
        if (requested <= 0 || requested > 5_000_000) {
            throw new IllegalArgumentException("n must be between 1 and 5000000");
        }
        if (days <= 0 || days > 90) {
            throw new IllegalArgumentException("days must be between 1 and 90");
        }

        long start = System.nanoTime();
        long inserted = switch (mode.trim().toLowerCase()) {
            case "jdbc" -> seedJdbc((int) requested, days);
            case "jpa" -> seedJpa((int) requested, days);
            default -> throw new IllegalArgumentException("mode must be 'jdbc' or 'jpa'");
        };
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        return new SeedResponse(requested, inserted, mode.toLowerCase(), elapsedMs, logEventRepository.count());
    }

    private long seedJdbc(int totalRows, int days) {
        final String sql = """
                INSERT INTO log_events (ts, service, level, endpoint, status, ip, trace_id, latency_ms, message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        long inserted = 0;
        int remaining = totalRows;
        while (remaining > 0) {
            int currentBatch = Math.min(CHUNK_SIZE, remaining);
            List<GeneratedLogEvent> batch = generator.generateBatch(currentBatch, days);
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    GeneratedLogEvent row = batch.get(i);
                    ps.setTimestamp(1, Timestamp.from(row.ts()));
                    ps.setString(2, row.service());
                    ps.setString(3, row.level());
                    ps.setString(4, row.endpoint());
                    if (row.status() == null) {
                        ps.setNull(5, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(5, row.status());
                    }
                    ps.setString(6, row.ip());
                    ps.setString(7, row.traceId());
                    if (row.latencyMs() == null) {
                        ps.setNull(8, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(8, row.latencyMs());
                    }
                    ps.setString(9, row.message());
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });

            inserted += currentBatch;
            remaining -= currentBatch;
        }
        return inserted;
    }

    @Transactional
    protected long seedJpa(int totalRows, int days) {
        long inserted = 0;
        int remaining = totalRows;
        while (remaining > 0) {
            int currentBatch = Math.min(CHUNK_SIZE, remaining);
            List<GeneratedLogEvent> batch = generator.generateBatch(currentBatch, days);

            for (GeneratedLogEvent row : batch) {
                LogEvent event = new LogEvent();
                event.setTs(row.ts());
                event.setService(row.service());
                event.setLevel(row.level());
                event.setEndpoint(row.endpoint());
                event.setStatus(row.status());
                event.setIp(row.ip());
                event.setTraceId(row.traceId());
                event.setLatencyMs(row.latencyMs());
                event.setMessage(row.message());
                entityManager.persist(event);
            }

            entityManager.flush();
            entityManager.clear();
            inserted += currentBatch;
            remaining -= currentBatch;
        }
        return inserted;
    }
}
