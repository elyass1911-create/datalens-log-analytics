package com.yassin.datalens.dto;

public record SuspiciousIpPoint(String ip, long totalRequests, long authFailures, long maxRequestsPerMinute,
                                double suspicionScore, String reasons) {
}
