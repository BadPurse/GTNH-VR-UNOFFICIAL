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

    private static void initFocusedTextFieldReflection() {
        if (focusedFieldAttempted) return;
        focusedFieldAttempted = true;
        try {
            Field f = me.eigenraven.lwjgl3ify.client.TextFieldHandler.class.getDeclaredField("focusedTextInput");
            f.setAccessible(true);
            focusedTextInputField = f;
        } catch (Throwable ignored) {}
    }

    /** Returns LWJGL3ify's globally focused field; do not use this alone to decide if a screen is text-editing. */
    public static net.minecraft.client.gui.GuiTextField getFocusedTextField() {
        initFocusedTextFieldReflection();
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

    /**
     * Returns the globally focused vanilla field only when that exact widget belongs to this
     * screen. TextFieldHandler.focusedTextInput is static; LWJGL3ify can otherwise leave a
     * reference to a field from a previous GUI after VR suppresses its SDL reset call.
     */
    public static net.minecraft.client.gui.GuiTextField getFocusedTextField(GuiScreen screen) {
        if (screen == null) return null;
        net.minecraft.client.gui.GuiTextField focused = getFocusedTextField(screen);
        return focused != null && focused.isFocused() && screenContainsObject(screen, focused)
                ? focused : null;
    }

    /** Clear LWJGL3ify's stale text-field pointer without calling SDL end/reset text input (unsafe in VR). */
    public static void clearFocusedTextField(net.minecraft.client.gui.GuiTextField expectedField) {
        initFocusedTextFieldReflection();
        boolean clearedExpectedField = expectedField == null;
        if (focusedTextInputField != null) {
            try {
                Object obj = focusedTextInputField.get(null);
                Object current = obj instanceof java.lang.ref.WeakReference<?> ref ? ref.get() : null;
                if (expectedField == null || current == expectedField) {
                    focusedTextInputField.set(null, new java.lang.ref.WeakReference<>(null));
                    clearedExpectedField = true;
                }
            } catch (Throwable t) {
                GTNHVRLog.warn("Unable to clear LWJGL3ify's focused text-field reference: %s", t);
            }
        }
        if (clearedExpectedField) {
            try {
                me.eigenraven.lwjgl3ify.client.TextFieldHandler.textBuffer.setLength(0);
            } catch (Throwable ignored) {}
        }
    }

    public static boolean hasAnyFocusedTextField(GuiScreen screen) {
        if (screen == null) return false;

        if (getFocusedTextField(screen) != null) {
            return true;
        }
        return getAnyFocusedTextFieldObject(screen) != null;
    }

    private static boolean screenContainsObject(GuiScreen screen, Object target) {
        if (screen == null || target == null) return false;
        java.util.IdentityHashMap<Object, Boolean> visited = new java.util.IdentityHashMap<>();
        return containsScreenObject(screen, target, 0, visited);
    }

    private static boolean containsScreenObject(Object owner, Object target, int depth,
                                                java.util.IdentityHashMap<Object, Boolean> visited) {
        if (owner == null) return false;
        if (owner == target) return true;
        if (depth > 5 || visited.size() > 512 || visited.put(owner, Boolean.TRUE) != null) return false;

        Class<?> clz = owner.getClass();
        while (clz != null && clz != Object.class) {
            for (Field field : clz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || field.getType().isPrimitive() || field.getType().isEnum()
                        || field.getType() == String.class) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value == target) return true;
                    if (value == null) continue;

                    if (value instanceof Iterable<?> iterable) {
                        for (Object element : iterable) {
                            if (element == target) return true;
                            if (isGuiObject(element) && containsScreenObject(element, target, depth + 1, visited)) {
                                return true;
                            }
                        }
                    } else if (value.getClass().isArray()) {
                        int length = java.lang.reflect.Array.getLength(value);
                        for (int i = 0; i < length; i++) {
                            Object element = java.lang.reflect.Array.get(value, i);
                            if (element == target) return true;
                            if (isGuiObject(element) && containsScreenObject(element, target, depth + 1, visited)) {
                                return true;
                            }
                        }
                    } else if (isGuiObject(value) && containsScreenObject(value, target, depth + 1, visited)) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
            clz = clz.getSuperclass();
        }
        return false;
    }

    private static boolean isGuiObject(Object value) {
        if (value == null) return false;
        // A child widget can belong to this screen, but a GuiScreen reference is usually a
        // parent/previous screen. Following that link would incorrectly validate a stale field
        // from the screen we just left and could auto-open the VR keyboard in an unrelated GUI.
        if (value instanceof GuiScreen) return false;

        String name = value.getClass().getName();
        return name.startsWith("net.minecraft.client.gui.")
                || name.startsWith("cpw.mods.fml.client.config.")
                || name.startsWith("appeng.client.gui.")
                || name.startsWith("codechicken.nei.")
                || name.startsWith("codechicken.lib.gui.")
                || name.startsWith("com.gtnewhorizons.modularui.")
                || name.startsWith("com.cleanroommc.modularui.");
    }

    /** Recognize standard and modded text widgets without accepting arbitrary focused buttons/panels. */
    private static boolean isTextInputCandidate(Object obj) {
        if (obj == null) return false;
        String simpleName = obj.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        boolean textFieldName = simpleName.contains("textfield") || simpleName.contains("textbox")
                || simpleName.contains("textinput") || simpleName.contains("searchfield")
                || simpleName.contains("editbox") || simpleName.contains("inputfield")
                || simpleName.contains("textentry") || simpleName.contains("searchbox");
        boolean focusedMethod = false;
        boolean textApi = false;
        boolean keyTypedMethod = false;
        try {
            for (Method method : obj.getClass().getMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                Class<?>[] params = method.getParameterTypes();
                if ((name.equals("isfocused") || name.equals("func_146206_l") || name.equals("hasfocus"))
                        && params.length == 0
                        && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                    focusedMethod = true;
                }
                if (name.equals("gettext") || name.equals("getvalue") || name.equals("getstring")
                        || name.equals("getmessage") || name.equals("writetext") || name.equals("inserttext")
                        || name.equals("settext") || name.equals("setvalue") || name.equals("setstring")
                        || name.equals("setmessage") || name.equals("deletefromcursor") || name.equals("func_146177_a")) {
                    textApi = true;
                }
                if ((name.contains("keytyped") || name.equals("func_146201_a"))
                        && params.length == 2 && params[0] == char.class && params[1] == int.class) {
                    keyTypedMethod = true;
                }
            }
        } catch (Throwable ignored) {}
        return textFieldName || (focusedMethod && (textApi || keyTypedMethod));
    }

    private static boolean isObjectFocused(Object obj) {
        if (obj == null) return false;
        try {
            for (String name : new String[] {"isFocused", "func_146206_l", "hasFocus"}) {
                try {
                    Method method = obj.getClass().getMethod(name);
                    if (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class) {
                        method.setAccessible(true);
                        return Boolean.TRUE.equals(method.invoke(obj));
                    }
                } catch (NoSuchMethodException ignored) {}
            }
            for (Class<?> clz = obj.getClass(); clz != null && clz != Object.class; clz = clz.getSuperclass()) {
                for (Field field : clz.getDeclaredFields()) {
                    if (field.getType() == boolean.class && (field.getName().equalsIgnoreCase("focused")
                            || field.getName().equalsIgnoreCase("isFocused") || field.getName().equalsIgnoreCase("hasFocus"))) {
                        field.setAccessible(true);
                        if (field.getBoolean(obj)) return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static Object getAnyFocusedTextFieldObject(GuiScreen screen) {
        if (screen == null) return null;
        return findFocusedTextFieldObject(screen, 0, new java.util.IdentityHashMap<>(), true);
    }

    private static Object findFocusedTextFieldObject(Object owner, int depth,
            java.util.IdentityHashMap<Object, Boolean> visited, boolean root) {
        if (owner == null || depth > 5 || visited.size() > 512
                || visited.put(owner, Boolean.TRUE) != null) return null;
        if (!root && isTextInputCandidate(owner) && isObjectFocused(owner)) return owner;

        for (Class<?> clz = owner.getClass(); clz != null && clz != Object.class; clz = clz.getSuperclass()) {
            for (Field field : clz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || field.getType().isPrimitive() || field.getType().isEnum()
                        || field.getType() == String.class) continue;
                try {
                    field.setAccessible(true);
                    Object found = findFocusedTextFieldChild(field.get(owner), depth + 1, visited);
                    if (found != null) return found;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static Object findFocusedTextFieldChild(Object value, int depth,
            java.util.IdentityHashMap<Object, Boolean> visited) {
        if (value == null || depth > 5) return null;
        if (value instanceof GuiScreen) return null;
        if (isTextInputCandidate(value) && isObjectFocused(value)) return value;

        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                Object found = findFocusedTextFieldChild(element, depth + 1, visited);
                if (found != null) return found;
            }
        } else if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object found = findFocusedTextFieldChild(java.lang.reflect.Array.get(value, i), depth + 1, visited);
                if (found != null) return found;
            }
        } else if (isGuiObject(value)) {
            return findFocusedTextFieldObject(value, depth, visited, false);
        }
        return null;
    }

    /** Queue input for editors that consume LWJGL events; replace rather than retain stale char-buffer contents. */
    public static void queueKeyboardEvent(char character, int keyCode) {
        try {
            me.eigenraven.lwjgl3ify.client.TextFieldHandler.textBuffer.setLength(0);
            if (character != '\0' && character != '\b' && character != '\r'
                    && character != '\t' && character != '\u001b') {
                me.eigenraven.lwjgl3ify.client.TextFieldHandler.textBuffer.append(character);
            }
        } catch (Throwable ignored) {}
        try {
            org.lwjglx.input.Keyboard.addCharEvent(character, keyCode);
        } catch (Throwable ignored) {}
    }

    /** Send input to a focused editor, preferring its own key handler to preserve cursor/selection semantics. */
    public static boolean dispatchKeyToFocusedTextField(GuiScreen screen, char character, int keyCode) {
        if (screen == null) return false;
        net.minecraft.client.gui.GuiTextField vanilla = getFocusedTextField(screen);
        if (vanilla != null && vanilla.isFocused()) {
            vanilla.textboxKeyTyped(character, keyCode);
            clearTextBuffer();
            return true;
        }
        Object target = getAnyFocusedTextFieldObject(screen);
        if (target == null) return false;
        boolean handled = dispatchKeyToTextFieldObject(target, character, keyCode);
        if (handled) clearTextBuffer();
        return handled;
    }

    public static boolean dispatchKeyToTextFieldObject(Object target, char character, int keyCode) {
        if (target == null) return false;
        try {
            java.util.ArrayList<Method> methods = new java.util.ArrayList<>();
            for (Method method : target.getClass().getMethods()) methods.add(method);
            for (Class<?> clz = target.getClass(); clz != null && clz != Object.class; clz = clz.getSuperclass()) {
                for (Method method : clz.getDeclaredMethods()) {
                    if (!methods.contains(method)) methods.add(method);
                }
            }

            for (Method method : methods) {
                Class<?>[] params = method.getParameterTypes();
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                if (params.length == 2 && params[0] == char.class && params[1] == int.class
                        && (name.equals("textboxkeytyped") || name.equals("func_146201_a")
                        || name.contains("keytyped"))) {
                    method.setAccessible(true);
                    method.invoke(target, character, keyCode);
                    return true;
                }
            }

            if (keyCode == org.lwjgl.input.Keyboard.KEY_BACK || keyCode == org.lwjgl.input.Keyboard.KEY_DELETE) {
                int amount = keyCode == org.lwjgl.input.Keyboard.KEY_BACK ? -1 : 1;
                for (Method method : methods) {
                    Class<?>[] params = method.getParameterTypes();
                    String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                    if (params.length == 1 && params[0] == int.class
                            && (name.equals("deletefromcursor") || name.equals("func_146177_a")
                            || name.equals("deletechars"))) {
                        method.setAccessible(true);
                        method.invoke(target, amount);
                        return true;
                    }
                }
            }
            if (character != '\0' && character != '\b' && character != '\r'
                    && character != '\t' && character != '\u001b') {
                String value = String.valueOf(character);
                for (Method method : methods) {
                    Class<?>[] params = method.getParameterTypes();
                    String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                    if (params.length == 1 && params[0] == String.class
                            && (name.equals("writetext") || name.equals("inserttext") || name.equals("appendtext"))) {
                        method.setAccessible(true);
                        method.invoke(target, value);
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("Unable to dispatch virtual keyboard input to focused widget: %s", t);
        }
        return false;
    }

    public static void clearTextBuffer() {
        try {
            me.eigenraven.lwjgl3ify.client.TextFieldHandler.textBuffer.setLength(0);
        } catch (Throwable ignored) {}
    }

    public static void keyTyped(GuiScreen screen, char character, int keyCode) {
        if (screen == null) return;

        // Keep LWJGL3ify's buffer and queued key event in sync, including delete/enter events.
        queueKeyboardEvent(character, keyCode);

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
            // Mod text field fallback: use the widget's own handler and supported deletion APIs.
            Object modTf = getAnyFocusedTextFieldObject(screen);
            if (modTf != null) {
                dispatchKeyToTextFieldObject(modTf, character, keyCode);
                clearTextBuffer();
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
                VRVirtualKeyboard.getInstance().showAutoKeyboard(screen);
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
