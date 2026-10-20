package com.gtnhvr.gui.keyboard;

public class KeyboardLayoutKeys {
    private final KeyboardLayout layout;
    private final KeyboardKey[][] normalLayer;
    private final KeyboardKey[][] shiftLayer;
    private final int maxColumns;

    public KeyboardLayoutKeys(KeyboardLayout layout,
                              KeyboardKey[][] normalLayer,
                              KeyboardKey[][] shiftLayer) {
        this.layout = layout;
        this.normalLayer = normalLayer;
        this.shiftLayer = shiftLayer;

        int columns = 0;
        for (KeyboardKey[] row : normalLayer) {
            columns = Math.max(columns, row.length);
        }
        this.maxColumns = columns;
    }

    public KeyboardLayout getLayout() {
        return layout;
    }

    public int getMaxColumns() {
        return maxColumns;
    }

    public KeyboardKey[][] getKeys(boolean shifted) {
        return shifted ? shiftLayer : normalLayer;
    }
}
