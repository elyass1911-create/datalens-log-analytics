package com.yassin.datalens.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "classpath:sql/deterministic_fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AnalyticsDeterministicIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("datalens")
            .withUsername("datalens")
            .withPassword("datalens");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void topEndpointsDeterministicOrderCountsAndLatency() throws Exception {
        String response = mockMvc.perform(get("/api/analytics/top-endpoints")
                        .param("service", "api")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-01T00:10:00Z")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(JsonPath.<String>read(response, "$[0].endpoint")).isEqualTo("/api/login");
        assertThat(JsonPath.<Integer>read(response, "$[0].requestCount")).isEqualTo(4);
        assertThat(JsonPath.<Double>read(response, "$[0].avgLatencyMs")).isEqualTo(250.0);

        assertThat(JsonPath.<String>read(response, "$[1].endpoint")).isEqualTo("/api/search");
        assertThat(JsonPath.<Integer>read(response, "$[1].requestCount")).isEqualTo(3);
        assertThat(JsonPath.<Double>read(response, "$[1].avgLatencyMs")).isEqualTo(500.0);

        assertThat(JsonPath.<String>read(response, "$[2].endpoint")).isEqualTo("/api/orders");
        assertThat(JsonPath.<Integer>read(response, "$[2].requestCount")).isEqualTo(3);
        assertThat(JsonPath.<Double>read(response, "$[2].avgLatencyMs")).isEqualTo(70.0);
    }

    @Test
    void slidingWindowDeterministicBuckets() throws Exception {
        String response = mockMvc.perform(get("/api/analytics/sliding-window-errors")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-01T00:10:00Z")
                        .param("windowMinutes", "5")
                        .param("stepMinutes", "5"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(JsonPath.<Integer>read(response, "$.length()")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(response, "$[0].totalCount")).isEqualTo(5);
        assertThat(JsonPath.<Integer>read(response, "$[0].errorCount")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(response, "$[1].totalCount")).isEqualTo(5);
        assertThat(JsonPath.<Integer>read(response, "$[1].errorCount")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(response, "$[2].totalCount")).isEqualTo(0);
        assertThat(JsonPath.<Integer>read(response, "$[2].errorCount")).isEqualTo(0);
    }

    @Test
    void suspiciousIpsDeterministicScoreAndReason() throws Exception {
        String response = mockMvc.perform(get("/api/analytics/suspicious-ips")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-01T00:10:00Z")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(JsonPath.<String>read(response, "$[0].ip")).isEqualTo("10.0.0.1");
        assertThat(JsonPath.<Integer>read(response, "$[0].totalRequests")).isEqualTo(6);
        assertThat(JsonPath.<Integer>read(response, "$[0].authFailures")).isEqualTo(4);
        assertThat(JsonPath.<Integer>read(response, "$[0].maxRequestsPerMinute")).isEqualTo(3);

        double score = JsonPath.read(response, "$[0].suspicionScore");
        assertThat(score).isBetween(13.09, 13.11);
        assertThat(JsonPath.<String>read(response, "$[0].reasons")).isEqualTo("auth_failures=4, max_rpm=3");
    }

    @Test
    void explainPlanShowsScanForOptimizedProfile() throws Exception {
        mockMvc.perform(post("/api/dev/indexes/apply").param("profile", "optimized"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/dev/explain")
                        .param("scenario", "topIps")
                        .param("profile", "optimized")
                        .param("variant", "base"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String plan = JsonPath.read(response, "$.plan");
        assertThat(plan).contains("Execution Time");
        assertThat(plan).contains("Scan");
    }
}
