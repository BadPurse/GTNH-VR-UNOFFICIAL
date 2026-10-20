package com.gtnhvr.settings;

public enum HeightMode {
    MATCH_MODEL("match_model"),
    REAL_SIZE("real_size");

    private final String key;

    HeightMode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
