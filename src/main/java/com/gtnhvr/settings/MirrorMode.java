package com.gtnhvr.settings;

public enum MirrorMode {
    OFF("off"),
    GUI("gui"),
    CROPPED("cropped"),
    SINGLE("single"),
    DUAL("dual"),
    FIRST_PERSON("first_person"),
    THIRD_PERSON("third_person"),
    MIXED_REALITY("mixed_reality");

    private final String key;

    MirrorMode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
