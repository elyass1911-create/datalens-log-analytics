package com.yassin.datalens.dto;

import java.time.Instant;

public record ErrorRatePoint(Instant bucketStart, long totalCount, long errorCount, double errorRate) {
}
