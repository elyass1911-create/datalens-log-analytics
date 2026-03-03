package com.yassin.datalens.dev;

public enum BenchmarkScenario {
    ERROR_RATE,
    TOP_IPS,
    P95_LATENCY;

    public static BenchmarkScenario from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("scenario is required");
        }
        String normalized = value.trim()
                .replace("-", "")
                .replace("_", "")
                .toLowerCase();

        return switch (normalized) {
            case "errorrate" -> ERROR_RATE;
            case "topips" -> TOP_IPS;
            case "p95latency" -> P95_LATENCY;
            default -> throw new IllegalArgumentException("Unsupported scenario. Use errorRate, topIps, or p95Latency");
        };
    }

    public String apiName() {
        return switch (this) {
            case ERROR_RATE -> "errorRate";
            case TOP_IPS -> "topIps";
            case P95_LATENCY -> "p95Latency";
        };
    }
}
