package com.gtnhvr.gui;

import com.gtnhvr.GTNHVRLog;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Lwjgl3Aware
public final class GuiHelper {
    private static Method mouseClickedMethod;
    private static Method mouseMovedOrUpMethod;
    private static Method mouseClickMoveMethod;
    private static Method keyTypedMethod;
    private static Method slotElementClickedMethod;
    private static boolean reflectionAttempted = false;

    private static long lastSlotClickTime = 0L;
    private static int lastSlotClickIndex = -1;

    private GuiHelper() {}

    private static synchronized void initReflection() {
        if (reflectionAttempted) return;
        reflectionAttempted = true;

        try {
            for (Method m : GuiScreen.class.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 3 && params[0] == int.class && params[1] == int.class && params[2] == int.class) {
                    String name = m.getName();
                    if (name.equals("mouseClicked") || name.equals("func_73864_a")) {
                        m.setAccessible(true);
                        mouseClickedMethod = m;
                    } else if (name.equals("mouseMovedOrUp") || name.equals("func_146286_b")) {
                        m.setAccessible(true);
                        mouseMovedOrUpMethod = m;
                    }
                } else if (params.length == 4 && params[0] == int.class && params[1] == int.class && params[2] == int.class && params[3] == long.class) {
                    String name = m.getName();
                    if (name.equals("mouseClickMove") || name.equals("func_146273_a")) {
                        m.setAccessible(true);
                        mouseClickMoveMethod = m;
                    }
                } else if (params.length == 2 && params[0] == char.class && params[1] == int.class) {
                    String name = m.getName();
                    if (name.equals("keyTyped") || name.equals("func_73869_a")) {
                        m.setAccessible(true);
                        keyTypedMethod = m;
                    }
                }
            }
            if (keyTypedMethod == null) {
                for (Method m : GuiScreen.class.getDeclaredMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0] == char.class && params[1] == int.class) {
                        m.setAccessible(true);
                        keyTypedMethod = m;
                        break;
                    }
                }
            }
            for (Method m : GuiSlot.class.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 4 && params[0] == int.class && params[1] == boolean.class && params[2] == int.class && params[3] == int.class) {
                    m.setAccessible(true);
                    slotElementClickedMethod = m;
                    break;
                }
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("Failed to inspect GuiScreen/GuiSlot reflection methods: %s", t);
        }
    }

    private static Field focusedTextInputField = null;
    private static boolean focusedFieldAttempted = false;

    public static net.minecraft.client.gui.GuiTextField getFocusedTextField() {
        if (!focusedFieldAttempted) {
            focusedFieldAttempted = true;
            try {
                Field f = me.eigenraven.lwjgl3ify.client.TextFieldHandler.class.getDeclaredField("focusedTextInput");
                f.setAccessible(true);
                focusedTextInputField = f;
            } catch (Throwable ignored) {}
        }
        if (focusedTextInputField != null) {
            try {
                Object obj = focusedTextInputField.get(null);
                if (obj instanceof java.lang.ref.WeakReference<?> ref) {
                    Object tf = ref.get();
                    if (tf instanceof net.minecraft.client.gui.GuiTextField guiTf) {
                        return guiTf;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static boolean hasAnyFocusedTextField(GuiScreen screen) {
        if (screen == null) return false;

        net.minecraft.client.gui.GuiTextField vanillaTf = getFocusedTextField();
        if (vanillaTf != null && vanillaTf.isFocused()) {
            return true;
        }

        return getAnyFocusedTextFieldObject(screen) != null;
    }

    public static Object getAnyFocusedTextFieldObject(GuiScreen screen) {
        if (screen == null) return null;
        try {
            Class<?> clz = screen.getClass();
            while (clz != null && clz != Object.class && clz != GuiScreen.class.getSuperclass()) {
                for (Field field : clz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object val = field.get(screen);
                    if (val == null) continue;

                    if (isObjectFocused(val)) {
                        return val;
                    }

                    if (val instanceof Iterable<?> iter) {
                        for (Object elem : iter) {
                            if (isObjectFocused(elem)) return elem;
                        }
                    } else if (val.getClass().isArray()) {
                        int len = java.lang.reflect.Array.getLength(val);
                        for (int i = 0; i < len; i++) {
                            Object elem = java.lang.reflect.Array.get(val, i);
                            if (isObjectFocused(elem)) return elem;
                        }
                    }
                }
                clz = clz.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isObjectFocused(Object obj) {
        if (obj == null) return false;
        try {
            Method m = null;
            try {
                m = obj.getClass().getMethod("isFocused");
            } catch (NoSuchMethodException e) {
                try {
                    m = obj.getClass().getMethod("func_146206_l");
                } catch (NoSuchMethodException ignored) {}
            }
            if (m != null && m.getReturnType() == boolean.class) {
                m.setAccessible(true);
                return (boolean) m.invoke(obj);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void keyTyped(GuiScreen screen, char character, int keyCode) {
        if (screen == null) return;

        // 1. Populate LWJGL3ify TextFieldHandler.textBuffer for printable chars
        if (character != '\0' && character != '\b' && character != '\r' && character != '\t' && character != '\u001b') {
            try {
                if (me.eigenraven.lwjgl3ify.client.TextFieldHandler.textBuffer.length() == 0) {
                    me.eigenraven.lwjgl3ify.client.TextFieldHandler.textBuffer.append(character);
                }
            } catch (Throwable ignored) {}
        }

        // 2. Queue char event into org.lwjglx.input.Keyboard for screens/mods polling Keyboard.next()
        try {
            org.lwjglx.input.Keyboard.addCharEvent(character, keyCode);
        } catch (Throwable ignored) {}

        // Check text field state before invoking screen.keyTyped to detect if it was handled
        net.minecraft.client.gui.GuiTextField focused = getFocusedTextField();
        String beforeText = null;
        int beforeCursor = -1;
        if (focused != null && focused.isFocused()) {
            try {
                beforeText = focused.getText();
                beforeCursor = focused.getCursorPosition();
            } catch (Throwable ignored) {}
        }

        // 3. Dispatch to GuiScreen.keyTyped
        initReflection();
        boolean invoked = false;
        if (keyTypedMethod != null) {
            try {
                keyTypedMethod.invoke(screen, character, keyCode);
                invoked = true;
            } catch (Throwable ignored) {}
        }
        if (!invoked) {
            try {
                Class<?> clz = screen.getClass();
                while (clz != null && clz != Object.class) {
                    for (Method m : clz.getDeclaredMethods()) {
                        Class<?>[] p = m.getParameterTypes();
                        if (p.length == 2 && p[0] == char.class && p[1] == int.class) {
                            m.setAccessible(true);
                            m.invoke(screen, character, keyCode);
                            invoked = true;
                            break;
                        }
                    }
                    if (invoked) break;
                    clz = clz.getSuperclass();
                }
            } catch (Throwable t) {
                GTNHVRLog.warn("Error invoking keyTyped on %s: %s", screen.getClass().getSimpleName(), t);
            }
        }

        // 4. Fallback direct dispatch to focused text field if screen.keyTyped did not consume/handle it
        if (focused != null && focused.isFocused()) {
            try {
                boolean textChanged = (beforeText != null && !beforeText.equals(focused.getText()))
                        || (beforeCursor >= 0 && beforeCursor != focused.getCursorPosition());
                if (!textChanged) {
                    focused.textboxKeyTyped(character, keyCode);
                }
                me.eigenraven.lwjgl3ify.client.TextFieldHandler.textBuffer.setLength(0);
            } catch (Throwable ignored) {}
        } else {
            // Mod text field fallback (MEGuiTextField in AE2, TextFieldEnder in EnderIO, etc.)
            Object modTf = getAnyFocusedTextFieldObject(screen);
            if (modTf != null) {
                try {
                    Method m = null;
                    try {
                        m = modTf.getClass().getMethod("textboxKeyTyped", char.class, int.class);
                    } catch (NoSuchMethodException e) {
                        try {
                            m = modTf.getClass().getMethod("func_146201_a", char.class, int.class);
                        } catch (NoSuchMethodException ignored) {}
                    }
                    if (m != null) {
                        m.setAccessible(true);
                        m.invoke(modTf, character, keyCode);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    public static void mouseClickMove(GuiScreen screen, int mouseX, int mouseY, int button) {
        if (screen == null) return;
        initReflection();
        if (mouseClickMoveMethod != null) {
            try {
                mouseClickMoveMethod.invoke(screen, mouseX, mouseY, button, 0L);
            } catch (Throwable ignored) {}
        }
    }

    public static void clickScreen(GuiScreen screen, int mouseX, int mouseY, int button) {
        if (screen == null) return;
        initReflection();

        // 1. Standard GuiScreen button click
        if (mouseClickedMethod != null) {
            try {
                mouseClickedMethod.invoke(screen, mouseX, mouseY, button);
            } catch (Throwable t) {
                GTNHVRLog.warn("Error invoking GuiScreen.mouseClicked: %s", t);
            }
        }

        // 2. Direct GuiSlot element click (fixes GuiSelectWorld, GuiMultiplayer, GuiLanguage, etc.)
        handleClickGuiSlot(screen, mouseX, mouseY, button);

        // 3. Universal text field focus detection (triggers VRVirtualKeyboard in AE2, EnderIO, vanilla, etc.)
        if (com.gtnhvr.settings.VRClientSettings.keyboardAutoLayout) {
            boolean hasFocused = hasAnyFocusedTextField(screen);
            if (hasFocused) {
                VRVirtualKeyboard.getInstance().showAutoKeyboard();
            } else if (!(screen instanceof net.minecraft.client.gui.GuiChat)
                    && !(screen instanceof net.minecraft.client.gui.inventory.GuiEditSign)
                    && !(screen instanceof net.minecraft.client.gui.GuiScreenBook)) {
                VRVirtualKeyboard.getInstance().hideAutoKeyboard();
            }
        }
    }

    private static void handleClickGuiSlot(GuiScreen screen, int mouseX, int mouseY, int button) {
        if (button != 0) return; // Primary trigger click only
        List<GuiSlot> slots = findGuiSlots(screen);
        for (GuiSlot slot : slots) {
            try {
                int slotIndex = slot.func_148124_c(mouseX, mouseY);
                if (slotIndex >= 0) {
                    long now = System.currentTimeMillis();
                    boolean doubleClick = (slotIndex == lastSlotClickIndex && (now - lastSlotClickTime) < 400L);
                    lastSlotClickTime = now;
                    lastSlotClickIndex = slotIndex;

                    Method m = findElementClickedMethod(slot.getClass());
                    if (m != null) {
                        if (m.getParameterTypes().length == 4) {
                            m.invoke(slot, slotIndex, doubleClick, mouseX, mouseY);
                        } else if (m.getParameterTypes().length == 2) {
                            m.invoke(slot, slotIndex, doubleClick);
                        }
                    }

                    // On double click in world select screen, immediately enter the world!
                    if (doubleClick && screen instanceof net.minecraft.client.gui.GuiSelectWorld worldSelect) {
                        try {
                            Method loadMethod = net.minecraft.client.gui.GuiSelectWorld.class.getDeclaredMethod("func_146615_e", int.class);
                            loadMethod.setAccessible(true);
                            loadMethod.invoke(worldSelect, slotIndex);
                        } catch (Throwable t) {
                            try {
                                Method loadMethod = net.minecraft.client.gui.GuiSelectWorld.class.getDeclaredMethod("selectWorld", int.class);
                                loadMethod.setAccessible(true);
                                loadMethod.invoke(worldSelect, slotIndex);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable t) {
                GTNHVRLog.warn("Failed to invoke GuiSlot.elementClicked: %s", t);
            }
        }
    }

    private static Method findElementClickedMethod(Class<?> clazz) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            for (Method m : curr.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 4 && p[0] == int.class && p[1] == boolean.class && p[2] == int.class && p[3] == int.class) {
                    m.setAccessible(true);
                    return m;
                }
                if (p.length == 2 && p[0] == int.class && p[1] == boolean.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    private static List<GuiSlot> findGuiSlots(GuiScreen screen) {
        List<GuiSlot> result = new ArrayList<>();
        Class<?> clazz = screen.getClass();
        while (clazz != null && clazz != Object.class && clazz != GuiScreen.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (GuiSlot.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object obj = f.get(screen);
                        if (obj instanceof GuiSlot s) {
                            result.add(s);
                        }
                    } catch (Throwable ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
        return result;
    }

    public static void releaseScreen(GuiScreen screen, int mouseX, int mouseY, int button) {
        if (screen == null) return;
        initReflection();
        if (mouseMovedOrUpMethod != null) {
            try {
                mouseMovedOrUpMethod.invoke(screen, mouseX, mouseY, button);
            } catch (Throwable t) {
                GTNHVRLog.warn("Error invoking GuiScreen.mouseMovedOrUp: %s", t);
            }
        }
    }
}
