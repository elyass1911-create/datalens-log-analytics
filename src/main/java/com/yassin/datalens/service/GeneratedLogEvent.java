package com.yassin.datalens.service;

import java.time.Instant;

public record GeneratedLogEvent(
        Instant ts,
        String service,
        String level,
        String endpoint,
        Integer status,
        String ip,
        String traceId,
        Integer latencyMs,
        String message
) {
}
