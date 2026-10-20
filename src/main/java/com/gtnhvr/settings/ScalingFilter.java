package com.gtnhvr.settings;

public enum ScalingFilter {
    LANCZOS("lanczos"),
    MITCHELL("mitchell"),
    BILINEAR("bilinear");

    private final String key;

    ScalingFilter(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
