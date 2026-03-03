package com.yassin.datalens.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class SyntheticLogEventGenerator {

    private static final List<String> SERVICES = List.of("auth", "api", "gateway", "search", "billing");

    private static final List<WeightedValue<String>> ENDPOINTS = List.of(
            new WeightedValue<>("/api/login", 12),
            new WeightedValue<>("/api/logout", 5),
            new WeightedValue<>("/api/users", 10),
            new WeightedValue<>("/api/orders", 14),
            new WeightedValue<>("/api/payments", 8),
            new WeightedValue<>("/api/search", 11),
            new WeightedValue<>("/api/products", 10),
            new WeightedValue<>("/health", 4),
            new WeightedValue<>("/admin/reports", 3),
            new WeightedValue<>("/internal/sync", 2)
    );

    private static final List<WeightedValue<Integer>> INFO_STATUS = List.of(
            new WeightedValue<>(200, 70),
            new WeightedValue<>(201, 8),
            new WeightedValue<>(204, 7),
            new WeightedValue<>(304, 5),
            new WeightedValue<>(400, 3),
            new WeightedValue<>(401, 2),
            new WeightedValue<>(429, 3),
            new WeightedValue<>(500, 2)
    );

    private static final List<WeightedValue<Integer>> WARN_STATUS = List.of(
            new WeightedValue<>(400, 20),
            new WeightedValue<>(401, 22),
            new WeightedValue<>(403, 16),
            new WeightedValue<>(404, 20),
            new WeightedValue<>(429, 12),
            new WeightedValue<>(500, 10)
    );

    private static final List<WeightedValue<Integer>> ERROR_STATUS = List.of(
            new WeightedValue<>(500, 45),
            new WeightedValue<>(502, 15),
            new WeightedValue<>(503, 15),
            new WeightedValue<>(401, 7),
            new WeightedValue<>(403, 8),
            new WeightedValue<>(429, 10)
    );

    private static final List<String> HOT_IPS = List.of(
            "185.199.108.11", "185.199.109.11", "103.21.244.7", "45.95.147.77", "91.240.118.172"
    );

    public List<GeneratedLogEvent> generateBatch(int n, int days) {
        if (n <= 0) {
            return List.of();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Instant now = Instant.now();
        List<GeneratedLogEvent> rows = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            String service = SERVICES.get(random.nextInt(SERVICES.size()));
            String level = pickLevel(random);
            String endpoint = pickWeighted(ENDPOINTS, random);
            Integer status = pickStatus(level, random);
            String ip = pickIp(random);
            int latency = sampleLatency(level, random);
            Instant ts = sampleTimestamp(now, days, random);
            String traceId = random.nextLong() + "-" + random.nextLong();
            String message = level + " event from " + service + " " + endpoint;

            rows.add(new GeneratedLogEvent(ts, service, level, endpoint, status, ip, traceId, latency, message));
        }
        return rows;
    }

    private String pickLevel(ThreadLocalRandom random) {
        int roll = random.nextInt(100);
        if (roll < 80) {
            return "INFO";
        }
        if (roll < 95) {
            return "WARN";
        }
        return "ERROR";
    }

    private Integer pickStatus(String level, ThreadLocalRandom random) {
        return switch (level) {
            case "INFO" -> pickWeighted(INFO_STATUS, random);
            case "WARN" -> pickWeighted(WARN_STATUS, random);
            default -> pickWeighted(ERROR_STATUS, random);
        };
    }

    private String pickIp(ThreadLocalRandom random) {
        if (random.nextDouble() < 0.18) {
            return HOT_IPS.get(random.nextInt(HOT_IPS.size()));
        }
        return random.nextInt(1, 255) + "." + random.nextInt(0, 255) + "." + random.nextInt(0, 255) + "."
                + random.nextInt(1, 255);
    }

    private int sampleLatency(String level, ThreadLocalRandom random) {
        int base = switch (level) {
            case "INFO" -> 40;
            case "WARN" -> 80;
            default -> 120;
        };

        double gaussian = Math.abs(random.nextGaussian());
        int spike = random.nextDouble() < 0.04 ? random.nextInt(400, 1800) : 0;
        return Math.min(3000, (int) (base + gaussian * 120 + spike));
    }

    private Instant sampleTimestamp(Instant now, int days, ThreadLocalRandom random) {
        int dayOffset = random.nextInt(Math.max(1, days));
        ZonedDateTime zdt = now.atZone(ZoneOffset.UTC).minusDays(dayOffset);
        int hour = sampleHour(random);
        int minute = random.nextInt(60);
        int second = random.nextInt(60);
        return zdt.withHour(hour).withMinute(minute).withSecond(second).withNano(random.nextInt(1_000_000_000)).toInstant();
    }

    private int sampleHour(ThreadLocalRandom random) {
        int[] weightedHours = {
                0, 1, 2, 3, 4, 5,
                6, 7, 8,
                9, 9, 10, 10, 10, 11, 11, 11, 12, 12, 12,
                13, 14, 15,
                16, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22,
                23
        };
        return weightedHours[random.nextInt(weightedHours.length)];
    }

    private <T> T pickWeighted(List<WeightedValue<T>> weightedValues, ThreadLocalRandom random) {
        int total = weightedValues.stream().mapToInt(WeightedValue::weight).sum();
        int roll = random.nextInt(total);
        int running = 0;
        for (WeightedValue<T> weightedValue : weightedValues) {
            running += weightedValue.weight();
            if (roll < running) {
                return weightedValue.value();
            }
        }
        return weightedValues.getLast().value();
    }

    private record WeightedValue<T>(T value, int weight) {
    }
}
