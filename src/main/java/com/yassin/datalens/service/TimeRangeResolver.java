package com.yassin.datalens.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class TimeRangeResolver {

    public TimeRange resolve(String from, String to, long defaultHoursBack) {
        Instant resolvedTo = parseOrDefault(to, Instant.now());
        Instant resolvedFrom = parseOrDefault(from, resolvedTo.minusSeconds(defaultHoursBack * 3600));

        if (!resolvedFrom.isBefore(resolvedTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
        return new TimeRange(resolvedFrom, resolvedTo);
    }

    private Instant parseOrDefault(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid timestamp format. Use ISO-8601 instant, e.g. 2026-03-01T10:15:30Z");
        }
    }
}
