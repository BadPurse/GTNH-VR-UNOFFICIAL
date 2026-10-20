package com.gtnhvr.settings;

/**
 * Which VR device controls the player's look direction (Visor RotationMode).
 * Snap or smooth stick turning is selected with the world rotation step slider.
 */
public enum RotationMode {
    OFFHAND("offhand"),
    MAIN_HAND("main_hand"),
    HMD("hmd");

    private final String key;

    RotationMode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /** Parses a stored value; legacy SNAP/SMOOTH entries and unknown strings fall back to the default. */
    public static RotationMode parse(String value, RotationMode fallback) {
        if (value == null) return fallback;
        try {
            return RotationMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
