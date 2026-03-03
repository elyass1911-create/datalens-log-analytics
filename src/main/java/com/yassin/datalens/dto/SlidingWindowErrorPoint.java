package com.yassin.datalens.dto;

import java.time.Instant;

public record SlidingWindowErrorPoint(Instant windowStart, long totalCount, long errorCount) {
}
