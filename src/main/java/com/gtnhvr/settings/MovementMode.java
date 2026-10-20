package com.gtnhvr.settings;

public enum MovementMode {
    CONTROLLER("controller"),
    TELEPORT("teleport"),
    HMD("hmd"),
    OFFHAND("offhand");

    private final String key;

    MovementMode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
