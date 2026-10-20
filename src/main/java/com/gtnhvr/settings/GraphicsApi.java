package com.gtnhvr.settings;

public enum GraphicsApi {
    AUTO("auto"),
    OPENGL("opengl"),
    VULKAN("vulkan");

    private final String key;

    GraphicsApi(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
