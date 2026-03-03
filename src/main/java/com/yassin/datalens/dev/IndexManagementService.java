package com.yassin.datalens.dev;

import com.yassin.datalens.dto.IndexOperationResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class IndexManagementService {

    private static final String TABLE = "log_events";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public IndexManagementService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public IndexOperationResponse apply(IndexProfile profile) {
        switch (profile) {
            case BASELINE -> {
                executeSqlScript("sql/index-profiles/baseline-apply.sql");
                executeSqlScript("sql/index-profiles/optimized-drop.sql");
            }
            case OPTIMIZED -> {
                executeSqlScript("sql/index-profiles/baseline-apply.sql");
                executeSqlScript("sql/index-profiles/optimized-apply.sql");
            }
        }

        return new IndexOperationResponse(profile.name().toLowerCase(), "apply",
                detectActiveProfile().name().toLowerCase(), "Index profile applied successfully");
    }

    public IndexOperationResponse drop(IndexProfile profile) {
        switch (profile) {
            case BASELINE -> executeSqlScript("sql/index-profiles/baseline-drop.sql");
            case OPTIMIZED -> executeSqlScript("sql/index-profiles/optimized-drop.sql");
        }

        return new IndexOperationResponse(profile.name().toLowerCase(), "drop",
                detectActiveProfile().name().toLowerCase(), "Index profile dropped successfully");
    }

    public IndexProfile detectActiveProfile() {
        Set<String> names = new LinkedHashSet<>(jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = ?
                """, String.class, TABLE));

        boolean baseline = names.contains("idx_log_event_ts");
        boolean optimized = names.containsAll(List.of(
                "idx_log_event_level_ts",
                "idx_log_event_service_ts",
                "idx_log_event_ip_ts",
                "idx_log_event_endpoint_ts",
                "idx_log_event_status_ts",
                "idx_log_event_service_level_ts"
        ));

        if (baseline && optimized) {
            return IndexProfile.OPTIMIZED;
        }
        return IndexProfile.BASELINE;
    }

    private void executeSqlScript(String classpathLocation) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(false, false, "UTF-8",
                new ClassPathResource(classpathLocation));
        populator.execute(dataSource);
    }
}
