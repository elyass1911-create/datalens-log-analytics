package com.yassin.datalens.dev;

public enum IndexProfile {
    BASELINE,
    OPTIMIZED;

    public static IndexProfile from(String value) {
        return IndexProfile.valueOf(value.trim().toUpperCase());
    }
}
