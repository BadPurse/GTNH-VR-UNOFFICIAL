package com.gtnhvr.input;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/**
 * Holds the mouse button state produced by the VR pointer so vanilla and Forge widgets that
 * poll {@code Mouse.isButtonDown} (GuiSlot lists such as the world selection, GuiScrollingList,
 * the creative scroll bar) see the VR trigger as a held left button. Widgets that listen to the
 * event queue are fed through {@code Mouse.addButtonEvent} by the controller handler.
 */
@Lwjgl3Aware
public final class VRMouseBridge {
    private static final boolean[] VR_BUTTONS = new boolean[8];

    private VRMouseBridge() {}

    public static void setButton(int button, boolean down) {
        if (button >= 0 && button < VR_BUTTONS.length) {
            VR_BUTTONS[button] = down;
        }
    }

    public static void releaseAll() {
        java.util.Arrays.fill(VR_BUTTONS, false);
    }

    public static boolean isButtonDown(int button) {
        if (button >= 0 && button < VR_BUTTONS.length && VR_BUTTONS[button]) {
            return true;
        }
        return org.lwjglx.input.Mouse.isButtonDown(button);
    }
}
