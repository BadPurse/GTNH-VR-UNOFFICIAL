package com.gtnhvr.gui;

public class GuiScaleHelper {
    private final float originWidth;
    private final float originHeight;

    private float scale;
    private int startX;
    private int startY;

    public GuiScaleHelper(float originWidth, float originHeight) {
        this.originWidth = originWidth;
        this.originHeight = originHeight;
    }

    public void computeScale(float screenWidth, float screenHeight) {
        scale = Math.min(screenWidth / originWidth, screenHeight / originHeight);
        startX = (int) ((screenWidth - originWidth * scale) / 2f);
        startY = (int) ((screenHeight - originHeight * scale) / 2f);
    }

    public int scaledX(float originX) {
        return startX + Math.round(originX * scale);
    }

    public int scaledY(float originY) {
        return startY + Math.round(originY * scale);
    }

    public int scaledSize(float originSize) {
        return Math.round(originSize * scale);
    }

    public float getScale() {
        return scale;
    }

    public int getStartX() {
        return startX;
    }

    public int getStartY() {
        return startY;
    }

    public float getOriginWidth() {
        return originWidth;
    }

    public float getOriginHeight() {
        return originHeight;
    }
}
