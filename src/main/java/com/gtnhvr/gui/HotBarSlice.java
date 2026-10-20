package com.gtnhvr.gui;

import net.minecraft.util.ResourceLocation;

/**
 * Slots of the radial hotbar selector, ported from Visor's HotBarSlice and the selection boxes of
 * VROverlayHotBar. Each slice knows its background texture, the item position inside the 98x98
 * hotbar image and the direction of its sector (angles in image space, y pointing down).
 */
public enum HotBarSlice {
    CENTER(0, "default", 41, 41, 0.0),
    TOP(1, "top", 41, 5, -Math.PI / 2.0),
    TOP_RIGHT(2, "top_right", 77, 5, -Math.PI / 4.0),
    RIGHT(3, "right", 77, 41, 0.0),
    BOTTOM_RIGHT(4, "bottom_right", 77, 77, Math.PI / 4.0),
    BOTTOM(5, "bottom", 41, 77, Math.PI / 2.0),
    BOTTOM_LEFT(6, "bottom_left", 5, 77, 3.0 * Math.PI / 4.0),
    LEFT(7, "left", 5, 41, Math.PI),
    TOP_LEFT(8, "top_left", 5, 5, -3.0 * Math.PI / 4.0),
    NOT_SELECTED(-1, "default", 0, 0, 0.0);

    private final int slot;
    private final ResourceLocation background;
    private final int itemX;
    private final int itemY;
    private final double sectorAngle;

    HotBarSlice(int slot, String texture, int itemX, int itemY, double sectorAngle) {
        this.slot = slot;
        this.background = new ResourceLocation("gtnhvr", "textures/gui/overlays/hotbar/" + texture + ".png");
        this.itemX = itemX;
        this.itemY = itemY;
        this.sectorAngle = sectorAngle;
    }

    public int getSlot() {
        return slot;
    }

    public ResourceLocation getBackground() {
        return background;
    }

    public int getItemX() {
        return itemX;
    }

    public int getItemY() {
        return itemY;
    }

    public double getSectorAngle() {
        return sectorAngle;
    }

    public static HotBarSlice fromSlot(int slot) {
        if (slot < 0 || slot > 8) {
            return NOT_SELECTED;
        }
        return values()[slot];
    }
}
