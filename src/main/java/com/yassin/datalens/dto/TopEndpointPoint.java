package com.yassin.datalens.dto;

public record TopEndpointPoint(String endpoint, long requestCount, double avgLatencyMs) {
}
