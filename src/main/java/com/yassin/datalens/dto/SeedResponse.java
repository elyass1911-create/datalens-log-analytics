package com.yassin.datalens.dto;

public record SeedResponse(long requested, long inserted, String mode, long elapsedMs, long totalRows) {
}
