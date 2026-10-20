package com.gtnhvr.settings;

public enum HeightUnits {
    METRIC("metric"),
    IMPERIAL("imperial");

    private final String key;

    HeightUnits(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
