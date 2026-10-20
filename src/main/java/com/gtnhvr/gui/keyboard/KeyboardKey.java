package com.gtnhvr.gui.keyboard;

public class KeyboardKey {
    private final String label;
    private final String input;
    private final int fallbackKey;
    private final int fallbackModifiers;

    public KeyboardKey(String label,
                       String input,
                       int fallbackKey,
                       int fallbackModifiers) {
        this.label = label;
        this.input = input;
        this.fallbackKey = fallbackKey;
        this.fallbackModifiers = fallbackModifiers;
    }

    public String getLabel() {
        return label;
    }

    public String getInput() {
        return input;
    }

    public int getFallbackKey() {
        return fallbackKey;
    }

    public int getFallbackModifiers() {
        return fallbackModifiers;
    }

    public boolean hasFallback() {
        return fallbackKey != -1;
    }
}
