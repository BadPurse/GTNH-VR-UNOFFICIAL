package com.gtnhvr.settings;

public enum VRPlayMode {
    ENABLED("enabled"),
    WORLD_ONLY("world_only"),
    DISABLED("disabled");

    private final String key;

    VRPlayMode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
