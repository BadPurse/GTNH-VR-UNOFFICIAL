package com.gtnhvr.settings;

public enum HeightAdjustment {
    CAMERA_OFFSET("camera_offset"),
    WORLD_SCALE("world_scale");

    private final String key;

    HeightAdjustment(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
