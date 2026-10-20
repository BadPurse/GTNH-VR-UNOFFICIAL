package com.gtnhvr.gui.keyboard;

import org.lwjgl.input.Keyboard;

public final class KeyboardRow {

    private static final int[] NUMBERS = {
            Keyboard.KEY_GRAVE,
            Keyboard.KEY_1,
            Keyboard.KEY_2,
            Keyboard.KEY_3,
            Keyboard.KEY_4,
            Keyboard.KEY_5,
            Keyboard.KEY_6,
            Keyboard.KEY_7,
            Keyboard.KEY_8,
            Keyboard.KEY_9,
            Keyboard.KEY_0,
            Keyboard.KEY_MINUS,
            Keyboard.KEY_EQUALS
    };

    private static final int[] TOP = {
            Keyboard.KEY_Q,
            Keyboard.KEY_W,
            Keyboard.KEY_E,
            Keyboard.KEY_R,
            Keyboard.KEY_T,
            Keyboard.KEY_Y,
            Keyboard.KEY_U,
            Keyboard.KEY_I,
            Keyboard.KEY_O,
            Keyboard.KEY_P,
            Keyboard.KEY_LBRACKET,
            Keyboard.KEY_RBRACKET,
            Keyboard.KEY_BACKSLASH
    };

    private static final int[] HOME = {
            Keyboard.KEY_A,
            Keyboard.KEY_S,
            Keyboard.KEY_D,
            Keyboard.KEY_F,
            Keyboard.KEY_G,
            Keyboard.KEY_H,
            Keyboard.KEY_J,
            Keyboard.KEY_K,
            Keyboard.KEY_L,
            Keyboard.KEY_SEMICOLON,
            Keyboard.KEY_APOSTROPHE
    };

    private static final int[] BOTTOM = {
            Keyboard.KEY_Z,
            Keyboard.KEY_X,
            Keyboard.KEY_C,
            Keyboard.KEY_V,
            Keyboard.KEY_B,
            Keyboard.KEY_N,
            Keyboard.KEY_M,
            Keyboard.KEY_COMMA,
            Keyboard.KEY_PERIOD,
            Keyboard.KEY_SLASH
    };

    private final int[] keyCodes;
    private final String[] normalSymbols;
    private final String[] shiftSymbols;

    private KeyboardRow(int[] keyCodes, String normal, String shift) {
        this.keyCodes = keyCodes;
        this.normalSymbols = split(normal);
        this.shiftSymbols = split(shift);

        if (this.normalSymbols.length != this.shiftSymbols.length) {
            throw new IllegalArgumentException(
                    "Both layers of a row must type the same number of keys: \""
                            + normal + "\" vs \"" + shift + "\""
            );
        }
    }

    public static KeyboardRow numbers(String normal, String shift) {
        return new KeyboardRow(NUMBERS, normal, shift);
    }

    public static KeyboardRow top(String normal, String shift) {
        return new KeyboardRow(TOP, normal, shift);
    }

    public static KeyboardRow home(String normal, String shift) {
        return new KeyboardRow(HOME, normal, shift);
    }

    public static KeyboardRow bottom(String normal, String shift) {
        return new KeyboardRow(BOTTOM, normal, shift);
    }

    public int size() {
        return normalSymbols.length;
    }

    public String symbol(int index, boolean shifted) {
        return shifted ? shiftSymbols[index] : normalSymbols[index];
    }

    public int keyCodeAt(int index) {
        return index < keyCodes.length ? keyCodes[index] : -1;
    }

    private static String[] split(String row) {
        int[] codePoints = row.codePoints().toArray();
        String[] symbols = new String[codePoints.length];
        for (int i = 0; i < codePoints.length; i++) {
            symbols[i] = new String(Character.toChars(codePoints[i]));
        }
        return symbols;
    }
}
