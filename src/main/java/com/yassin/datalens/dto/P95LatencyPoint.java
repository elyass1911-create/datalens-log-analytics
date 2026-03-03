package com.yassin.datalens.dto;

import java.time.Instant;

public record P95LatencyPoint(Instant bucketStart, double p95LatencyMs) {
}
