package com.yassin.datalens.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalyticsIntegrationTest {

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

    @BeforeAll
    void seedData() throws Exception {
        mockMvc.perform(post("/api/dev/indexes/apply").param("profile", "optimized"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/dev/seed")
                        .param("n", "5000")
                        .param("mode", "jdbc")
                        .param("days", "14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(5000));
    }

    @Test
    void analyticsEndpointsReturnData() throws Exception {
        mockMvc.perform(get("/api/analytics/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dbUp").value(true))
                .andExpect(jsonPath("$.rowCount", greaterThan(0)));

        mockMvc.perform(get("/api/analytics/p95-latency").param("bucketMinutes", "5"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/analytics/suspicious-ips").param("limit", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void topIpsLimitAndSortingWork() throws Exception {
        String response = mockMvc.perform(get("/api/analytics/top-ips").param("limit", "5"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer size = JsonPath.read(response, "$.length()");
        assertThat(size).isEqualTo(5);

        long prev = Long.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            Number currentValue = JsonPath.read(response, "$[" + i + "].errorLikeCount");
            long current = currentValue.longValue();
            assertThat(current).isLessThanOrEqualTo(prev);
            prev = current;
        }
    }

    @Test
    void errorRateBucketsAscAndRateBounded() throws Exception {
        String from = Instant.now().minusSeconds(48 * 3600).toString();
        String to = Instant.now().toString();

        String response = mockMvc.perform(get("/api/analytics/error-rate")
                        .param("from", from)
                        .param("to", to)
                        .param("bucketMinutes", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer size = JsonPath.read(response, "$.length()");
        assertThat(size).isGreaterThan(0);

        Instant prev = Instant.MIN;
        for (int i = 0; i < size; i++) {
            String bucketStart = JsonPath.read(response, "$[" + i + "].bucketStart");
            Instant current = Instant.parse(bucketStart);
            assertThat(current).isAfterOrEqualTo(prev);
            Number rateValue = JsonPath.read(response, "$[" + i + "].errorRate");
            double rate = rateValue.doubleValue();
            assertThat(rate).isBetween(0.0, 1.0);
            prev = current;
        }
    }

    @Test
    void indexApplyDropAndBenchmarkWork() throws Exception {
        mockMvc.perform(post("/api/dev/indexes/drop").param("profile", "optimized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("drop"));

        mockMvc.perform(post("/api/dev/indexes/apply").param("profile", "optimized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("apply"));

        mockMvc.perform(post("/api/dev/benchmark/run")
                        .param("scenario", "errorRate")
                        .param("iterations", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iterations").value(3))
                .andExpect(jsonPath("$.variants[0].meanMs", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.variants[0].maxMs", lessThanOrEqualTo(10_000.0)));
    }

    @Test
    void explainWorksForAtLeastTwoScenarios() throws Exception {
        String errorRateExplain = mockMvc.perform(get("/api/dev/explain")
                        .param("scenario", "errorRate")
                        .param("profile", "optimized")
                        .param("variant", "date_trunc_bucket"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String topIpsExplain = mockMvc.perform(get("/api/dev/explain")
                        .param("scenario", "topIps")
                        .param("profile", "optimized")
                        .param("variant", "base"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String errorRatePlan = JsonPath.read(errorRateExplain, "$.plan");
        String topIpsPlan = JsonPath.read(topIpsExplain, "$.plan");
        assertThat(errorRatePlan).contains("Execution Time");
        assertThat(topIpsPlan).contains("Execution Time");
    }
}
