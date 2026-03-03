package com.yassin.datalens.dto;

import java.util.List;

public record BenchmarkStatsResponse(String scenario, String profile, int iterations, List<VariantStat> variants,
                                     long totalDurationMs) {

    public record VariantStat(String variant, double meanMs, double p95Ms, double minMs, double maxMs) {
    }
}
