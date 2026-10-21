package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.settings.VROverlayConfig;
import com.gtnhvr.GTNHVRLog;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.gui.keyboard.KeyboardKey;
import com.gtnhvr.gui.keyboard.KeyboardLayout;
import com.gtnhvr.gui.keyboard.KeyboardLayoutKeys;
import com.gtnhvr.gui.keyboard.KeyboardLayouts;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * 1:1 Visor Virtual Keyboard for GTNH VR.
 * <p>
 * Implements the authentic Visor keyboard system:
 * - All 9 international layouts (English, Russian, Ukrainian, German, French, Spanish, Italian, Portuguese, Hungarian)
 * - Exact Visor layout, proportions, and key positioning
 * - Vanilla Minecraft GuiButton textures (widgets.png) and FontRenderer typography
 * - Top control row: Close (x), CUT, COPY, PASTE, Language cycle button
 * - Side keys: ESC, TAB, Shift, BKSP, ENTER
 * - Navigation arrows: Up, Down, Left, Right
 * - Visor 1:1 sleek Drag Bar and Resize Square underneath
 * - Full settings integration with layout selection and auto-detection
 */
@Lwjgl3Aware
public final class VRVirtualKeyboard {
    private static final VRVirtualKeyboard INSTANCE = new VRVirtualKeyboard();

    public enum KeyboardHandle {
        NONE, KEYS, DRAG_BAR, RESIZE_HANDLE
    }

    public static class KeyBtn extends GuiButton {
        public final Runnable action;
        public final java.util.function.Consumer<Boolean> actionWithHand;
        public boolean isShiftToggle = false;
        public boolean isShiftActive = false;
        public boolean isHoveredExternal = false;

        public KeyBtn(int id, int x, int y, int width, int height, String text, Runnable action) {
            super(id, x, y, width, height, text);
            this.action = action;
            this.actionWithHand = null;
        }

        public KeyBtn(int id, int x, int y, int width, int height, String text, java.util.function.Consumer<Boolean> actionWithHand) {
            super(id, x, y, width, height, text);
            this.actionWithHand = actionWithHand;
            this.action = () -> actionWithHand.accept(false);
        }

        public void run(boolean isLeft) {
            if (actionWithHand != null) {
                actionWithHand.accept(isLeft);
            } else if (action != null) {
                action.run();
            }
        }

        @Override
        public int getHoverState(boolean hovered) {
            if (isShiftToggle && isShiftActive) {
                return 2;
            }
            if (isHoveredExternal) {
                return 2;
            }
            return super.getHoverState(hovered);
        }
    }

    public static class PointerState {
        public final boolean isLeft;
        public boolean tracked = false;
        public boolean hit = false;
        public KeyboardHandle handle = KeyboardHandle.NONE;
        public int hoveredKeyIndex = -1;
        public final Vector2f hitUV = new Vector2f(-1.0F, -1.0F);
        public final Vector3f hitPoint = new Vector3f();
        public boolean triggerWasDown = false;
        public KeyBtn heldButton = null;
        public int holdTicks = 0;

        public PointerState(boolean isLeft) {
            this.isLeft = isLeft;
        }

        public void reset() {
            tracked = false;
            hit = false;
            handle = KeyboardHandle.NONE;
            hoveredKeyIndex = -1;
            hitUV.set(-1.0F, -1.0F);
            triggerWasDown = false;
            heldButton = null;
            holdTicks = 0;
        }
    }

    private static final int TEX_W = 1024;
    private static final int TEX_H = 512;
    private static final int KB_PIXEL_W = 424;
    private static final int KB_PIXEL_H = 168;
    private static final float RENDER_SCALE = 2.0F;

    private boolean visible = false;
    private boolean shiftPressed = false;
    private KeyboardLayout activeLayout = KeyboardLayout.ENGLISH;

    // 3D Pose in room space
    private final Vector3f kbPos = new Vector3f(0.0F, 0.95F, -1.10F);
    private final Quaternionf kbRot = new Quaternionf();
    private float kbWidth = 1.05F;  // meters
    private float kbHeight = 0.416F; // meters

    // Dual pointer states for simultaneous typing
    private final PointerState leftPointer = new PointerState(true);
    private final PointerState rightPointer = new PointerState(false);
    private boolean dragHandIsLeft = false;

    private KeyboardHandle hoveredHandle = KeyboardHandle.NONE;
    private boolean isDragging = false;
    private boolean isResizing = false;

    private final Vector3f dragLocalOffset = new Vector3f();
    private final Quaternionf dragLocalRot = new Quaternionf();
    private float initialHandDist = 1.0F;
    private float initialKbWidth = 1.05F;

    private int hoveredKeyIndex = -1;
    private final Vector2f lastHitUV = new Vector2f(-1.0F, -1.0F);
    private final Vector3f lastHitPoint = new Vector3f();

    private final List<KeyBtn> buttons = new ArrayList<>();

    // OpenGL FBO & Texture
    private int fbo = 0;
    private int textureId = 0;
    private int depthRb = 0;
    private boolean needsRedraw = true;
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    private VRVirtualKeyboard() {
        initLayoutFromSettings();
        rebuildButtons();
        resetDefaultPose();
    }

    public static VRVirtualKeyboard getInstance() {
        return INSTANCE;
    }

    private boolean autoOpened = false;
    private boolean sizeApplied = false;
    /** Temporarily override the normal overlay visibility setting for controller binding capture. */
    private boolean forceVisibleForBindingCapture = false;
    /** Screen that owns an automatically opened keyboard, mirroring Visor's attachedTo field. */
    private net.minecraft.client.gui.GuiScreen autoOwnerScreen;
    /** Screen that owns a manually opened keyboard; null means it was opened in-world. */
    private net.minecraft.client.gui.GuiScreen manualOwnerScreen;

    /** Compatibility overload: the active GUI is the keyboard's owner. */
    public void showAutoKeyboard() {
        Minecraft mc = Minecraft.getMinecraft();
        showAutoKeyboard(mc == null ? null : mc.currentScreen);
    }

    /**
     * Show the keyboard for one specific GUI, like Visor's showKeyboard(attachTo).
     * A manually opened keyboard is not silently converted to an auto keyboard; this
     * prevents unrelated text-input events from changing its visibility lifetime.
     */
    public void showAutoKeyboard(net.minecraft.client.gui.GuiScreen owner) {
        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.KEYBOARD);
        if (owner == null || !cfg.visible || !VRClientSettings.keyboardAutoLayout
                || !isTextInputContext(owner)) {
            return;
        }

        if (visible && !autoOpened) {
            // Preserve a manually/static opened keyboard instead of hijacking its lifecycle.
            return;
        }
        if (visible && autoOpened && autoOwnerScreen == owner) {
            return;
        }

        boolean reposition = visible && autoOpened && autoOwnerScreen != owner;
        this.autoOpened = true;
        this.autoOwnerScreen = owner;
        this.manualOwnerScreen = null;

        if (!visible) {
            setVisible(true);
        } else if (reposition) {
            // A new text-entry screen acquired focus without a reliable GuiOpen event.
            orientForHeadset();
            leftPointer.reset();
            rightPointer.reset();
        }
    }

    public void hideAutoKeyboard() {
        if (this.visible && this.autoOpened) {
            setVisible(false);
        }
    }

    public void initLayoutFromSettings() {
        try {
            List<KeyboardLayout> effective = KeyboardLayouts.getEffectiveSelected();
            if (!effective.isEmpty()) {
                activeLayout = effective.get(0);
            } else {
                activeLayout = KeyboardLayout.ENGLISH;
            }
        } catch (Throwable ignored) {
            activeLayout = KeyboardLayout.ENGLISH;
        }
    }

    public void resetDefaultPose() {
        orientForHeadset();
        float scale = VROverlayConfig.get(VROverlayConfig.KEYBOARD).scale;
        kbWidth = 1.05F * scale;
        kbHeight = kbWidth * (168.0F / 424.0F);
    }

    public void orientForHeadset() {
        org.lwjgl.openxr.XrPosef head = com.gtnhvr.OpenXRRuntime.getHeadPose();
        if (head == null) {
            kbPos.set(0.0F, 0.80F, -1.10F);
            kbRot.identity().rotateX((float) Math.toRadians(-25.0F));
            return;
        }
        float hx = head.position$().x();
        float hy = head.position$().y();
        float hz = head.position$().z();
        Quaternionf hmdRot = new Quaternionf(
                head.orientation().x(),
                head.orientation().y(),
                head.orientation().z(),
                head.orientation().w()
        );

        Vector3f forward = hmdRot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        forward.y = 0.0F;
        if (forward.lengthSquared() < 1e-4F) {
            forward.set(0.0F, 0.0F, -1.0F);
        } else {
            forward.normalize();
        }

        // Place the keyboard in front of the user and slightly below the GUI center.
        float kbDistance = VROverlayConfig.get(VROverlayConfig.KEYBOARD).distance;
        kbPos.set(hx + forward.x * kbDistance, hy - 0.52F, hz + forward.z * kbDistance);
        float yawRad = (float) Math.PI + (float) Math.atan2(forward.x, forward.z);
        kbRot.identity().rotateY(yawRad).rotateX((float) Math.toRadians(-25.0F));
        needsRedraw = true;
    }

    public boolean isVisible() {
        return visible;
    }

    /** Keep both automatic and manual keyboards attached to their owning screen. */
    public void onGuiOpen(net.minecraft.client.gui.GuiScreen nextScreen) {
        if (!visible) return;

        if (autoOpened) {
            if (nextScreen != autoOwnerScreen) {
                hideAutoKeyboard();
            }
            return;
        }

        // A manually opened in-world keyboard has no owner; close it when a GUI appears.
        if (manualOwnerScreen == null ? nextScreen != null : nextScreen != manualOwnerScreen) {
            setVisible(false);
        }
    }

    public void showForBindingCapture() {
        forceVisibleForBindingCapture = true;
        autoOpened = false;
        autoOwnerScreen = null;
        Minecraft mc = Minecraft.getMinecraft();
        // Claim the settings screen even if the keyboard was already visible for a text field.
        manualOwnerScreen = mc == null ? null : mc.currentScreen;
        setVisible(true);
        // If the keyboard was already open, refresh the top-row actions for capture mode.
        rebuildButtons();
    }

    public void hideForBindingCapture() {
        setVisible(false);
        forceVisibleForBindingCapture = false;
    }

    public void setVisible(boolean visible) {
        if (visible && !forceVisibleForBindingCapture
                && !VROverlayConfig.get(VROverlayConfig.KEYBOARD).visible) {
            return;
        }
        if (!visible) {
            this.autoOpened = false;
            this.autoOwnerScreen = null;
            this.manualOwnerScreen = null;
            this.forceVisibleForBindingCapture = false;
        }
        if (this.visible != visible) {
            this.visible = visible;
            if (visible) {
                Minecraft mc = Minecraft.getMinecraft();
                this.manualOwnerScreen = this.autoOpened || mc == null ? null : mc.currentScreen;
                if (!sizeApplied) {
                    sizeApplied = true;
                    float scale = VROverlayConfig.get(VROverlayConfig.KEYBOARD).scale;
                    kbWidth = 1.05F * scale;
                    kbHeight = kbWidth * (168.0F / 424.0F);
                }
                initLayoutFromSettings();
                rebuildButtons();
                orientForHeadset();
                needsRedraw = true;
                leftPointer.reset();
                rightPointer.reset();
            } else {
                this.autoOpened = false;
                this.manualOwnerScreen = null;
                hoveredHandle = KeyboardHandle.NONE;
                hoveredKeyIndex = -1;
                isDragging = false;
                isResizing = false;
                leftPointer.reset();
                rightPointer.reset();
            }
        }
    }

    public void toggleVisible() {
        this.autoOpened = false;
        setVisible(!this.visible);
    }

    public boolean isHovered() {
        return visible && (leftPointer.handle != KeyboardHandle.NONE || rightPointer.handle != KeyboardHandle.NONE
                || leftPointer.hoveredKeyIndex >= 0 || rightPointer.hoveredKeyIndex >= 0
                || hoveredHandle != KeyboardHandle.NONE || hoveredKeyIndex >= 0);
    }

    public KeyboardHandle getHoveredHandle() {
        if (rightPointer.handle != KeyboardHandle.NONE) return rightPointer.handle;
        if (leftPointer.handle != KeyboardHandle.NONE) return leftPointer.handle;
        return hoveredHandle;
    }

    public PointerState getPointer(boolean isLeft) {
        return isLeft ? leftPointer : rightPointer;
    }

    public PointerState getLeftPointer() {
        return leftPointer;
    }

    public PointerState getRightPointer() {
        return rightPointer;
    }

    public boolean isDragging() {
        return isDragging;
    }

    public boolean isResizing() {
        return isResizing;
    }

    public void startDragging(Vector3f handPos, Quaternionf handRot) {
        if (handPos == null || handRot == null) return;
        isDragging = true;
        dragLocalOffset.set(kbPos).sub(handPos);
        new Quaternionf(handRot).conjugate().transform(dragLocalOffset);
        dragLocalRot.set(handRot).conjugate().mul(kbRot);
    }

    public void updateDragging(Vector3f handPos, Quaternionf handRot) {
        if (!isDragging || handPos == null || handRot == null) return;
        Vector3f newPos = new Vector3f(dragLocalOffset);
        handRot.transform(newPos);
        newPos.add(handPos);
        kbPos.set(newPos);

        Quaternionf newRot = new Quaternionf(handRot).mul(dragLocalRot);
        kbRot.set(newRot);
    }

    public void stopDragging() {
        isDragging = false;
    }

    public void startResizing(Vector3f handPos) {
        if (handPos == null) return;
        initialHandDist = Math.max(0.1F, new Vector3f(handPos).sub(kbPos).length());
        initialKbWidth = kbWidth;
        isResizing = true;
    }

    public void updateResizing(Vector3f handPos) {
        if (!isResizing || handPos == null) return;
        float currentDist = new Vector3f(handPos).sub(kbPos).length();
        float ratio = currentDist / initialHandDist;
        kbWidth = Math.max(0.60F, Math.min(2.50F, initialKbWidth * ratio));
        kbHeight = kbWidth * (168.0F / 424.0F);
    }

    public void stopResizing() {
        isResizing = false;
    }

    public Vector3f getDragBarWorldPos() {
        float halfW = kbWidth * 0.5F;
        float halfH = kbHeight * 0.5F;
        float barGap = halfH * 0.04F;
        float barHalfH = halfH * 0.025F;
        float barCenterY = -halfH - barGap - barHalfH;
        Vector3f local = new Vector3f(0.0F, barCenterY, 0.001F);
        return new Vector3f(kbPos).add(new Quaternionf(kbRot).transform(local));
    }

    public Vector3f getResizeHandleWorldPos() {
        float halfW = kbWidth * 0.5F;
        float halfH = kbHeight * 0.5F;
        float barHalfW = halfW * 0.18F;
        float barHalfH = halfH * 0.025F;
        float barGap = halfH * 0.04F;
        float barCenterY = -halfH - barGap - barHalfH;
        float handleGap = barHalfW * 0.20F;
        float handleSide = barHalfH * 1.3F;
        float localX = barHalfW + handleGap + handleSide;
        Vector3f local = new Vector3f(localX, barCenterY, 0.001F);
        return new Vector3f(kbPos).add(new Quaternionf(kbRot).transform(local));
    }

    public Vector3f getLastHitPoint() {
        if (rightPointer.hit) return rightPointer.hitPoint;
        if (leftPointer.hit) return leftPointer.hitPoint;
        return lastHitPoint;
    }

    public Vector3f getLastHitPoint(boolean isLeft) {
        PointerState p = isLeft ? leftPointer : rightPointer;
        if (p.hit) return p.hitPoint;
        return lastHitPoint;
    }

    public void setLastHitPoint(Vector3f p) {
        if (p != null) this.lastHitPoint.set(p);
    }

    public Vector3f getKbPos() {
        return kbPos;
    }

    public Quaternionf getKbRot() {
        return kbRot;
    }

    public float getKbWidth() {
        return kbWidth;
    }

    public float getKbHeight() {
        return kbHeight;
    }

    public void cycleLayout() {
        cycleLayout(false);
    }

    public void cycleLayout(boolean isLeft) {
        List<KeyboardLayout> layouts = KeyboardLayouts.getEffectiveSelected();
        if (layouts.isEmpty()) {
            activeLayout = KeyboardLayout.ENGLISH;
        } else {
            int idx = layouts.indexOf(activeLayout);
            if (idx < 0 || idx >= layouts.size() - 1) {
                activeLayout = layouts.get(0);
            } else {
                activeLayout = layouts.get(idx + 1);
            }
        }
        VRClientSettings.activeKeyboardLayout = activeLayout.name();
        VRClientSettings.save();
        rebuildButtons();
        triggerHaptic(isLeft);
    }

    public void repositionForChat() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        org.lwjgl.openxr.XrPosef head = OpenXRRuntime.getHeadPose();
        if (head == null) return;

        Vector3f hmdPos = new Vector3f(head.position$().x(), head.position$().y(), head.position$().z());
        Quaternionf hmdRot = new Quaternionf(head.orientation().x(), head.orientation().y(), head.orientation().z(), head.orientation().w());

        Vector3f forward = hmdRot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        forward.y = 0.0F;
        if (forward.lengthSquared() > 1e-4F) {
            forward.normalize();
        } else {
            forward.set(0.0F, 0.0F, -1.0F);
        }

        kbPos.set(hmdPos.x + forward.x * 0.95F, hmdPos.y - 0.55F, hmdPos.z + forward.z * 0.95F);
        float yawRad = (float) Math.PI + (float) Math.atan2(forward.x, forward.z);
        kbRot.identity().rotateY(yawRad).rotateX((float) Math.toRadians(-32.0F));
        kbWidth = 1.05F;
        kbHeight = 0.416F;
    }

    public void rebuildButtons() {
        buttons.clear();

        int gridStart = 32;
        int keyGap = 2;
        int keyWidth = 25;
        int keyHeight = 20;
        int smallButtonWidth = 30;
        int sideButtonWidth = 35;
        int shiftButtonWidth = 50;
        int languageButtonWidth = 38;

        KeyboardLayoutKeys layoutKeys = KeyboardLayouts.get(activeLayout);
        KeyboardKey[][] rows = layoutKeys.getKeys(shiftPressed);
        int maxColumns = layoutKeys.getMaxColumns();
        int gridRightX = gridStart + maxColumns * (keyWidth + keyGap);

        int id = 0;
        int topY = gridStart - (keyHeight + keyGap);

        final boolean bindingCapture = isCapturingVirtualBinding();
        // 1. Close button (x). During capture it cancels the pending assignment.
        buttons.add(new KeyBtn(id++, 0, topY, smallButtonWidth, keyHeight, "§cx", (isLeft) -> {
            if (cancelVirtualBindingCapture()) {
                hideForBindingCapture();
            } else {
                setVisible(false);
            }
            triggerHaptic(isLeft);
        }));

        // 2. Left modifier in capture mode; otherwise CUT.
        buttons.add(new KeyBtn(id++, gridStart, topY, sideButtonWidth, keyHeight,
                bindingCapture ? "CTRL" : "CUT", (isLeft) -> {
            if (captureVirtualBindingKey(Keyboard.KEY_LCONTROL)) {
                triggerHaptic(isLeft);
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.currentScreen != null) {
                try {
                    net.minecraft.client.gui.GuiTextField focused = com.gtnhvr.gui.GuiHelper.getFocusedTextField(mc.currentScreen);
                    if (focused != null && focused.isFocused()) {
                        String sel = focused.getSelectedText();
                        if (sel != null && !sel.isEmpty()) {
                            GuiScreen.setClipboardString(sel);
                            focused.writeText("");
                            triggerHaptic(isLeft);
                            return;
                        }
                    }
                } catch (Throwable ignored) {}
                dispatchKey(mc, '\u0018', Keyboard.KEY_X);
            }
            triggerHaptic(isLeft);
        }));

        // 3. Right modifier in capture mode; otherwise COPY.
        buttons.add(new KeyBtn(id++, gridStart + sideButtonWidth + keyGap, topY, sideButtonWidth, keyHeight,
                bindingCapture ? "ALT" : "COPY", (isLeft) -> {
            if (captureVirtualBindingKey(Keyboard.KEY_LMENU)) {
                triggerHaptic(isLeft);
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.currentScreen != null) {
                try {
                    net.minecraft.client.gui.GuiTextField focused = com.gtnhvr.gui.GuiHelper.getFocusedTextField();
                    if (focused != null && focused.isFocused()) {
                        String sel = focused.getSelectedText();
                        if (sel != null && !sel.isEmpty()) {
                            GuiScreen.setClipboardString(sel);
                            triggerHaptic(isLeft);
                            return;
                        }
                    }
                } catch (Throwable ignored) {}
                dispatchKey(mc, '\u0003', Keyboard.KEY_C);
            }
            triggerHaptic(isLeft);
        }));

        // 4. Clear the selected binding in capture mode; otherwise PASTE.
        buttons.add(new KeyBtn(id++, gridStart + 2 * (sideButtonWidth + keyGap), topY, sideButtonWidth, keyHeight,
                bindingCapture ? "CLR" : "PASTE", (isLeft) -> {
            if (clearVirtualBindingCapture()) {
                triggerHaptic(isLeft);
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.currentScreen != null) {
                String clip = GuiScreen.getClipboardString();
                if (clip != null && !clip.isEmpty()) {
                    try {
                        net.minecraft.client.gui.GuiTextField focused = com.gtnhvr.gui.GuiHelper.getFocusedTextField();
                        if (focused != null && focused.isFocused()) {
                            focused.writeText(clip);
                            triggerHaptic(isLeft);
                            return;
                        }
                    } catch (Throwable ignored) {}
                    for (char c : clip.toCharArray()) {
                        dispatchKey(mc, c, 0);
                    }
                }
            }
            triggerHaptic(isLeft);
        }));

        // 5. Language button
        buttons.add(new KeyBtn(id++, gridStart + 3 * (sideButtonWidth + keyGap), topY, languageButtonWidth, keyHeight, activeLayout.getLabel(), (isLeft) -> cycleLayout(isLeft)));

        // Rows of characters
        for (int r = 0; r < rows.length; ++r) {
            KeyboardKey[] rowKeys = rows[r];
            int rowStartX = gridStart + ((maxColumns - rowKeys.length) * (keyWidth + keyGap)) / 2;
            int rowY = gridStart + r * (keyHeight + keyGap);

            for (int col = 0; col < rowKeys.length; ++col) {
                KeyboardKey key = rowKeys[col];
                buttons.add(new KeyBtn(id++, rowStartX + col * (keyWidth + keyGap), rowY, keyWidth, keyHeight, key.getLabel(), (isLeft) -> pressKey(key, isLeft)));
            }
        }

        int bottomY = gridStart + rows.length * (keyHeight + keyGap);
        int spaceX = gridStart + ((maxColumns - 5) / 2) * (keyWidth + keyGap);

        // Side buttons
        buttons.add(new KeyBtn(id++, 0, gridStart, smallButtonWidth, keyHeight, "ESC", (isLeft) -> pressActionKey('\u001b', Keyboard.KEY_ESCAPE, isLeft)));
        buttons.add(new KeyBtn(id++, 0, gridStart + keyHeight + keyGap, smallButtonWidth, keyHeight, "TAB", (isLeft) -> pressActionKey('\t', Keyboard.KEY_TAB, isLeft)));

        KeyBtn shiftBtn = new KeyBtn(id++, 0, gridStart + (rows.length - 1) * (keyHeight + keyGap), shiftButtonWidth, keyHeight, shiftPressed ? "SHIFT" : "Shift", (isLeft) -> {
            // In controller-binding capture mode this button selects the actual Shift key,
            // rather than merely toggling the keyboard's character layout.
            if (captureVirtualBindingKey(Keyboard.KEY_LSHIFT)) {
                triggerHaptic(isLeft);
                return;
            }
            shiftPressed = !shiftPressed;
            rebuildButtons();
            triggerHaptic(isLeft);
        });
        shiftBtn.isShiftToggle = true;
        shiftBtn.isShiftActive = shiftPressed;
        buttons.add(shiftBtn);

        buttons.add(new KeyBtn(id++, gridRightX, gridStart, sideButtonWidth, keyHeight, "BKSP", (isLeft) -> pressActionKey('\b', Keyboard.KEY_BACK, isLeft)));
        buttons.add(new KeyBtn(id++, gridRightX, gridStart + 2 * (keyHeight + keyGap), sideButtonWidth, keyHeight, "ENTER", (isLeft) -> pressActionKey('\r', Keyboard.KEY_RETURN, isLeft)));

        // Bottom row
        buttons.add(new KeyBtn(id++, spaceX, bottomY, 5 * (keyWidth + keyGap), keyHeight, " ", (isLeft) -> pressActionKey(' ', Keyboard.KEY_SPACE, isLeft)));
        buttons.add(new KeyBtn(id++, (maxColumns - 1) * (keyWidth + keyGap) + gridStart, bottomY, keyWidth, keyHeight, "↑", (isLeft) -> pressActionKey('\0', Keyboard.KEY_UP, isLeft)));
        buttons.add(new KeyBtn(id++, (maxColumns - 2) * (keyWidth + keyGap) + gridStart, bottomY + keyHeight + keyGap, keyWidth, keyHeight, "←", (isLeft) -> pressActionKey('\0', Keyboard.KEY_LEFT, isLeft)));
        buttons.add(new KeyBtn(id++, (maxColumns - 1) * (keyWidth + keyGap) + gridStart, bottomY + keyHeight + keyGap, keyWidth, keyHeight, "↓", (isLeft) -> pressActionKey('\0', Keyboard.KEY_DOWN, isLeft)));
        buttons.add(new KeyBtn(id++, maxColumns * (keyWidth + keyGap) + gridStart, bottomY + keyHeight + keyGap, keyWidth, keyHeight, "→", (isLeft) -> pressActionKey('\0', Keyboard.KEY_RIGHT, isLeft)));

        needsRedraw = true;
    }

    private void dispatchKey(Minecraft mc, char ch, int keyCode) {
        if (mc == null || mc.currentScreen == null) return;
        if (captureVirtualBindingKey(keyCode)) return;

        GuiScreen screen = mc.currentScreen;
        boolean containerScreen = screen instanceof net.minecraft.client.gui.inventory.GuiContainer;

        // In inventories/NEI the virtual keyboard must only type into the focused search or
        // text field. Sending E/ESC/etc. to the parent GuiContainer also runs its global
        // key handling and can close the inventory even though the user only meant to type.
        if (containerScreen) {
            dispatchDirectlyToFocusedTextField(screen, ch, keyCode);
            return;
        }

        // For ordinary focused text fields, bypass GuiScreen.keyTyped and the global LWJGL
        // event queue. That makes letters into text, rather than global Minecraft keybinds.
        if (dispatchDirectlyToFocusedTextField(screen, ch, keyCode)) {
            // Chat and sign/book screens still need screen-level handling for Enter/Escape.
            if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_ESCAPE)
                    && (screen instanceof net.minecraft.client.gui.GuiChat
                    || screen instanceof net.minecraft.client.gui.inventory.GuiEditSign
                    || screen instanceof net.minecraft.client.gui.GuiScreenBook)) {
                com.gtnhvr.gui.GuiHelper.keyTyped(screen, ch, keyCode);
            }
            return;
        }

        // Retain the legacy handlers only for explicit text-entry screens whose editors are
        // not exposed as focused text-field objects. Never forward arbitrary keys from an
        // inventory or unrelated GUI to Minecraft's global key handling.
        if (screen instanceof net.minecraft.client.gui.GuiChat
                || screen instanceof net.minecraft.client.gui.inventory.GuiEditSign
                || screen instanceof net.minecraft.client.gui.GuiScreenBook) {
            com.gtnhvr.gui.GuiHelper.keyTyped(screen, ch, keyCode);
        }
    }

    private boolean dispatchDirectlyToFocusedTextField(GuiScreen screen, char ch, int keyCode) {
        if (screen == null) return false;

        net.minecraft.client.gui.GuiTextField focused = com.gtnhvr.gui.GuiHelper.getFocusedTextField();
        if (focused != null && focused.isFocused()) {
            focused.textboxKeyTyped(ch, keyCode);
            return true;
        }

        Object modTextField = com.gtnhvr.gui.GuiHelper.getAnyFocusedTextFieldObject(screen);
        if (modTextField == null) return false;

        try {
            java.lang.reflect.Method method = null;
            try {
                method = modTextField.getClass().getMethod("textboxKeyTyped", char.class, int.class);
            } catch (NoSuchMethodException ignored) {
                try {
                    method = modTextField.getClass().getMethod("func_146201_a", char.class, int.class);
                } catch (NoSuchMethodException ignoredAgain) {
                    for (java.lang.reflect.Method candidate : modTextField.getClass().getMethods()) {
                        Class<?>[] params = candidate.getParameterTypes();
                        if (params.length == 2 && params[0] == char.class && params[1] == int.class
                                && (candidate.getName().toLowerCase(java.util.Locale.ROOT).contains("keytyped")
                                || candidate.getName().equals("func_146201_a"))) {
                            method = candidate;
                            break;
                        }
                    }
                }
            }
            if (method == null) return false;
            method.setAccessible(true);
            method.invoke(modTextField, ch, keyCode);
            return true;
        } catch (Throwable t) {
            GTNHVRLog.warn("Unable to dispatch virtual key directly to focused text field: %s", t);
            return false;
        }
    }

    private static boolean isTextInputContext(GuiScreen screen) {
        if (screen == null) return false;
        return screen instanceof net.minecraft.client.gui.GuiChat
                || screen instanceof net.minecraft.client.gui.inventory.GuiEditSign
                || screen instanceof net.minecraft.client.gui.GuiScreenBook
                || screen instanceof net.minecraft.client.gui.GuiRepair
                || com.gtnhvr.gui.GuiHelper.hasAnyFocusedTextField(screen);
    }

    private boolean isCapturingVirtualBinding() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !(mc.currentScreen instanceof com.gtnhvr.gui.VRSettingsScreen settings)) return false;
        com.gtnhvr.gui.settings.VROptionsSet options = settings.getCurrentOptionsSet();
        return options instanceof com.gtnhvr.gui.settings.categories.controls.VRSettingsControllerBindings bindings
                && bindings.isCapturingBinding();
    }

    private boolean cancelVirtualBindingCapture() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !(mc.currentScreen instanceof com.gtnhvr.gui.VRSettingsScreen settings)) return false;
        com.gtnhvr.gui.settings.VROptionsSet options = settings.getCurrentOptionsSet();
        if (options instanceof com.gtnhvr.gui.settings.categories.controls.VRSettingsControllerBindings bindings
                && bindings.isCapturingBinding()) {
            bindings.cancelVirtualKeyboardCapture();
            return true;
        }
        return false;
    }

    private boolean clearVirtualBindingCapture() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !(mc.currentScreen instanceof com.gtnhvr.gui.VRSettingsScreen settings)) return false;
        com.gtnhvr.gui.settings.VROptionsSet options = settings.getCurrentOptionsSet();
        if (options instanceof com.gtnhvr.gui.settings.categories.controls.VRSettingsControllerBindings bindings
                && bindings.isCapturingBinding()) {
            bindings.clearBindingFromVirtualKeyboard();
            return true;
        }
        return false;
    }

    /**
     * Routes a virtual key directly to the active controller-binding capture. That way a
     * physical keyboard is not required and the selected key is not accidentally typed into
     * the settings GUI. In capture mode even Escape is a bindable key on the virtual keyboard.
     */
    private boolean captureVirtualBindingKey(int keyCode) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !(mc.currentScreen instanceof com.gtnhvr.gui.VRSettingsScreen settings)) {
            return false;
        }
        com.gtnhvr.gui.settings.VROptionsSet options = settings.getCurrentOptionsSet();
        if (options instanceof com.gtnhvr.gui.settings.categories.controls.VRSettingsControllerBindings bindings
                && bindings.isCapturingBinding()) {
            return bindings.handleVirtualKeyboardKey(keyCode);
        }
        return false;
    }

    private void pressKey(KeyboardKey key, boolean isLeft) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen != null) {
            String input = key.getInput();
            if (input != null && !input.isEmpty()) {
                for (char c : input.toCharArray()) {
                    dispatchKey(mc, c, key.getFallbackKey());
                }
            } else if (key.hasFallback()) {
                dispatchKey(mc, '\0', key.getFallbackKey());
            }
        }
        triggerHaptic(isLeft);
    }

    private void pressActionKey(char ch, int keyCode, boolean isLeft) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen != null) {
            dispatchKey(mc, ch, keyCode);
        }
        triggerHaptic(isLeft);
    }

    private void triggerHaptic(boolean isLeft) {
        VRControllerHandler handler = VRControllerHandler.getInstance();
        handler.triggerHaptic(isLeft, 5_000_000L, 0.45F, 0);
    }

    private void checkFbo() {
        if (fbo == 0) {
            try {
                fbo = GLStateManager.glGenFramebuffers();
                textureId = GLStateManager.glGenTextures();
                depthRb = GLStateManager.glGenRenderbuffers();

                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
                GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, TEX_W, TEX_H, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

                GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRb);
                GLStateManager.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, TEX_W, TEX_H);

                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textureId, 0);
                GLStateManager.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRb);
                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            } catch (Throwable t) {
                GTNHVRLog.warn("Failed to create virtual keyboard FBO: %s", t);
            }
        }
    }

    public void renderToFbo(Minecraft mc, int mouseX, int mouseY) {
        checkFbo();
        if (fbo == 0 || mc == null) return;

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS | GL11.GL_VIEWPORT_BIT);
        int prevFbo = GLStateManager.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        try {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GLStateManager.glViewport(0, 0, TEX_W, TEX_H);
            GLStateManager.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();
            GLStateManager.glOrtho(0, TEX_W, TEX_H, 0, 1000, 3000);

            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();
            GLStateManager.glTranslatef(0.0F, 0.0F, -2000.0F);
            GLStateManager.glScalef(RENDER_SCALE, RENDER_SCALE, 1.0F);

            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // Subtle dark translucent backing panel
            Gui.drawRect(0, 0, KB_PIXEL_W, KB_PIXEL_H, 0x88121620);

            // Render each button using Minecraft's widgets.png button renderer
            mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/widgets.png"));

            int leftIdx = (leftPointer.hit && leftPointer.handle == KeyboardHandle.KEYS) ? leftPointer.hoveredKeyIndex : -1;
            int rightIdx = (rightPointer.hit && rightPointer.handle == KeyboardHandle.KEYS) ? rightPointer.hoveredKeyIndex : -1;

            for (KeyBtn btn : buttons) {
                btn.isHoveredExternal = (btn.id == leftIdx || btn.id == rightIdx);
                btn.drawButton(mc, mouseX, mouseY);
            }

            GLStateManager.glPopMatrix();
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPopMatrix();
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        } finally {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GLStateManager.glPopAttrib();
            needsRedraw = false;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    public boolean raycastPointer(Vector3f rayOrigin, Vector3f rayDir, PointerState pointer) {
        if (!visible || rayOrigin == null || rayDir == null) {
            pointer.hit = false;
            pointer.handle = KeyboardHandle.NONE;
            pointer.hoveredKeyIndex = -1;
            return false;
        }

        Vector3f planeNorm = new Vector3f(0.0F, 0.0F, 1.0F);
        kbRot.transform(planeNorm);

        float denom = rayDir.dot(planeNorm);
        if (Math.abs(denom) < 1e-4F) {
            pointer.hit = false;
            pointer.handle = KeyboardHandle.NONE;
            pointer.hoveredKeyIndex = -1;
            return false;
        }

        Vector3f diff = new Vector3f(kbPos).sub(rayOrigin);
        float t = diff.dot(planeNorm) / denom;
        if (t <= 0.0F || t > 5.0F) {
            pointer.hit = false;
            pointer.handle = KeyboardHandle.NONE;
            pointer.hoveredKeyIndex = -1;
            return false;
        }

        Vector3f hitPoint = new Vector3f(rayOrigin).add(new Vector3f(rayDir).mul(t));
        pointer.hitPoint.set(hitPoint);
        Vector3f localHit = new Vector3f(hitPoint).sub(kbPos);
        new Quaternionf(kbRot).conjugate().transform(localHit);

        float halfW = kbWidth * 0.5F;
        float halfH = kbHeight * 0.5F;

        // Visor handle dimensions
        float barHalfW = halfW * 0.18F;
        float barHalfH = halfH * 0.025F;
        float barGap = halfH * 0.04F;
        float barCenterY = -halfH - barGap - barHalfH;
        float barLeft = -barHalfW;
        float barRight = barHalfW;
        float barBottom = barCenterY - barHalfH;
        float barTop = barCenterY + barHalfH;

        float handleGap = barHalfW * 0.20F;
        float handleSide = barHalfH * 1.3F;
        float resizeLeft = barRight + handleGap;
        float resizeRight = resizeLeft + handleSide * 2.0F;
        float resizeBottom = barCenterY - handleSide;
        float resizeTop = barCenterY + handleSide;

        Minecraft mc = Minecraft.getMinecraft();
        boolean allowHandles = (mc != null && mc.theWorld != null);

        if (allowHandles) {
            // Drag bar hit
            if (localHit.x >= barLeft - 0.015F && localHit.x <= barRight + 0.015F &&
                localHit.y >= barBottom - 0.015F && localHit.y <= barTop + 0.015F) {
                pointer.hit = true;
                pointer.handle = KeyboardHandle.DRAG_BAR;
                pointer.hoveredKeyIndex = -1;
                return true;
            }

            // Resize handle hit
            if (localHit.x >= resizeLeft - 0.015F && localHit.x <= resizeRight + 0.015F &&
                localHit.y >= resizeBottom - 0.015F && localHit.y <= resizeTop + 0.015F) {
                pointer.hit = true;
                pointer.handle = KeyboardHandle.RESIZE_HANDLE;
                pointer.hoveredKeyIndex = -1;
                return true;
            }
        }

        // Keyboard quad hit
        if (localHit.x >= -halfW && localHit.x <= halfW && localHit.y >= -halfH && localHit.y <= halfH) {
            pointer.hit = true;
            pointer.handle = KeyboardHandle.KEYS;
            float u = (localHit.x + halfW) / kbWidth;
            float v = (halfH - localHit.y) / kbHeight; // invert Y for GUI space
            pointer.hitUV.set(u, v);

            int mouseX = (int) (u * (float) KB_PIXEL_W);
            int mouseY = (int) (v * (float) KB_PIXEL_H);

            int hitIdx = -1;
            for (int i = 0; i < buttons.size(); i++) {
                KeyBtn b = buttons.get(i);
                if (mouseX >= b.xPosition && mouseX < b.xPosition + b.width &&
                    mouseY >= b.yPosition && mouseY < b.yPosition + b.height) {
                    hitIdx = i;
                    break;
                }
            }
            pointer.hoveredKeyIndex = hitIdx;
            return true;
        }

        pointer.hit = false;
        pointer.handle = KeyboardHandle.NONE;
        pointer.hoveredKeyIndex = -1;
        return false;
    }

    public boolean raycastCheck(Vector3f rayOrigin, Vector3f rayDir, Vector2f outUV, Vector3f outHitPoint) {
        boolean hit = raycastPointer(rayOrigin, rayDir, rightPointer.tracked ? rightPointer : leftPointer);
        PointerState p = rightPointer.tracked ? rightPointer : leftPointer;
        if (hit) {
            if (outUV != null) outUV.set(p.hitUV);
            if (outHitPoint != null) outHitPoint.set(p.hitPoint);
            hoveredHandle = p.handle;
            hoveredKeyIndex = p.hoveredKeyIndex;
        }
        return hit;
    }

    public boolean raycast(Vector3f rayOrigin, Vector3f rayDir) {
        return raycastCheck(rayOrigin, rayDir, null, null);
    }

    private void updatePointerInteraction(PointerState p, VRControllerHandler handler) {
        if (!p.tracked || !p.hit) {
            p.triggerWasDown = false;
            p.heldButton = null;
            p.holdTicks = 0;
            return;
        }

        float trigger = p.isLeft ? handler.getLeftTriggerValue() : handler.getRightTriggerValue();
        boolean primary = p.isLeft ? handler.isLeftPrimaryValue() : handler.isRightPrimaryValue();
        float grip = p.isLeft ? handler.getLeftGripValue() : handler.getRightGripValue();

        // 1. Handles: Drag & Resize initiation (Trigger or Grip allowed)
        boolean handlePress = (trigger > 0.35F || grip > 0.35F);
        if (p.handle == KeyboardHandle.DRAG_BAR) {
            if (handlePress && !isDragging && !isResizing) {
                Vector3f handPos = p.isLeft ? handler.getLeftHandPos() : handler.getRightHandPos();
                Quaternionf handRot = p.isLeft ? handler.getLeftHandRot() : handler.getRightHandRot();
                startDragging(handPos, handRot);
                dragHandIsLeft = p.isLeft;
                handler.setKbDragHand(p.isLeft);
                triggerHaptic(p.isLeft);
            }
            return;
        } else if (p.handle == KeyboardHandle.RESIZE_HANDLE) {
            if (handlePress && !isDragging && !isResizing) {
                Vector3f handPos = p.isLeft ? handler.getLeftHandPos() : handler.getRightHandPos();
                startResizing(handPos);
                dragHandIsLeft = p.isLeft;
                handler.setKbDragHand(p.isLeft);
                triggerHaptic(p.isLeft);
            }
            return;
        }

        // 2. Keys typing: strictly Trigger or Primary button (NO GRIP to prevent stuck clicks!)
        boolean clickPressed = (trigger > 0.35F || primary);
        boolean clickReleased = (trigger < 0.20F && !primary);

        if (clickPressed && !p.triggerWasDown) {
            p.triggerWasDown = true;
            if (p.handle == KeyboardHandle.KEYS && p.hoveredKeyIndex >= 0 && p.hoveredKeyIndex < buttons.size()) {
                p.heldButton = buttons.get(p.hoveredKeyIndex);
                p.holdTicks = 0;
                if (p.heldButton != null) {
                    p.heldButton.run(p.isLeft);
                }
            }
        } else if (clickPressed && p.triggerWasDown && p.heldButton != null) {
            p.holdTicks++;
            // Natural keyboard repeat delay: ~400ms (36 frames), repeat rate: ~60ms (5 frames)
            if (p.holdTicks > 36 && ((p.holdTicks - 36) % 5 == 0)) {
                p.heldButton.run(p.isLeft);
            }
        } else if (clickReleased) {
            p.triggerWasDown = false;
            p.heldButton = null;
            p.holdTicks = 0;
        }
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (!visible || mc == null || handler == null) return;

        // Auto keyboards are attached to one exact GUI. Do not keep showing the
        // keyboard merely because a different screen happens to contain a focused/stale field.
        if (autoOpened && (!VRClientSettings.keyboardAutoLayout
                || !VROverlayConfig.get(VROverlayConfig.KEYBOARD).visible
                || mc.currentScreen != autoOwnerScreen
                || !isTextInputContext(autoOwnerScreen))) {
            hideAutoKeyboard();
            return;
        }

        // Manually opened keyboards are scoped to their owning GUI. An in-world keyboard
        // (null owner) must close as soon as a GUI becomes active.
        if (!autoOpened && (manualOwnerScreen == null
                ? mc.currentScreen != null
                : mc.currentScreen != manualOwnerScreen)) {
            setVisible(false);
            return;
        }

        leftPointer.tracked = handler.isLeftHandTracked();
        rightPointer.tracked = handler.isRightHandTracked();

        int prevLeftIdx = leftPointer.hoveredKeyIndex;
        int prevRightIdx = rightPointer.hoveredKeyIndex;

        // 1. Raycast both hands onto keyboard
        if (leftPointer.tracked) {
            Vector3f lPos = handler.getLeftHandPos();
            Quaternionf lRot = handler.getLeftHandRot();
            Vector3f lDir = lRot.transform(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());
            raycastPointer(lPos, lDir, leftPointer);
        } else {
            leftPointer.reset();
        }

        if (rightPointer.tracked) {
            Vector3f rPos = handler.getRightHandPos();
            Quaternionf rRot = handler.getRightHandRot();
            Vector3f rDir = rRot.transform(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());
            raycastPointer(rPos, rDir, rightPointer);
        } else {
            rightPointer.reset();
        }

        // Backward compatibility for single-pointer state
        if (rightPointer.hit) {
            hoveredHandle = rightPointer.handle;
            hoveredKeyIndex = rightPointer.hoveredKeyIndex;
            lastHitUV.set(rightPointer.hitUV);
            lastHitPoint.set(rightPointer.hitPoint);
        } else if (leftPointer.hit) {
            hoveredHandle = leftPointer.handle;
            hoveredKeyIndex = leftPointer.hoveredKeyIndex;
            lastHitUV.set(leftPointer.hitUV);
            lastHitPoint.set(leftPointer.hitPoint);
        } else {
            hoveredHandle = KeyboardHandle.NONE;
            hoveredKeyIndex = -1;
        }

        // Determine mouse coordinates for button hover state
        int mouseX = -100;
        int mouseY = -100;
        if (rightPointer.hit && rightPointer.handle == KeyboardHandle.KEYS && rightPointer.hitUV.x >= 0.0F) {
            mouseX = (int) (rightPointer.hitUV.x * (float) KB_PIXEL_W);
            mouseY = (int) (rightPointer.hitUV.y * (float) KB_PIXEL_H);
        } else if (leftPointer.hit && leftPointer.handle == KeyboardHandle.KEYS && leftPointer.hitUV.x >= 0.0F) {
            mouseX = (int) (leftPointer.hitUV.x * (float) KB_PIXEL_W);
            mouseY = (int) (leftPointer.hitUV.y * (float) KB_PIXEL_H);
        }

        boolean hoverChanged = (leftPointer.hoveredKeyIndex != prevLeftIdx || rightPointer.hoveredKeyIndex != prevRightIdx);
        if (mouseX != lastMouseX || mouseY != lastMouseY || hoverChanged || needsRedraw) {
            renderToFbo(mc, mouseX, mouseY);
        }

        // 2. Drag / Resize ongoing update
        if (isDragging || isResizing) {
            float trigger = dragHandIsLeft ? handler.getLeftTriggerValue() : handler.getRightTriggerValue();
            float grip = dragHandIsLeft ? handler.getLeftGripValue() : handler.getRightGripValue();
            if (trigger < 0.25F && grip < 0.25F) {
                stopDragging();
                stopResizing();
            } else {
                Vector3f handPos = dragHandIsLeft ? handler.getLeftHandPos() : handler.getRightHandPos();
                Quaternionf handRot = dragHandIsLeft ? handler.getLeftHandRot() : handler.getRightHandRot();
                if (isDragging) {
                    updateDragging(handPos, handRot);
                    setLastHitPoint(getDragBarWorldPos());
                } else {
                    updateResizing(handPos);
                    setLastHitPoint(getResizeHandleWorldPos());
                }
                return;
            }
        }

        // 3. Process key typing & handle clicks for both hands independently!
        updatePointerInteraction(leftPointer, handler);
        updatePointerInteraction(rightPointer, handler);
    }

    public void render(Minecraft mc) {
        if (!visible || mc == null) return;
        checkFbo();
        if (textureId == 0) return;
        if (needsRedraw) {
            renderToFbo(mc, lastMouseX, lastMouseY);
        }

        float halfW = kbWidth * 0.5F;
        float halfH = kbHeight * 0.5F;

        float barHalfW = halfW * 0.18F;
        float barHalfH = halfH * 0.025F;
        float barGap = halfH * 0.04F;
        float barCenterY = -halfH - barGap - barHalfH;
        float barLeft = -barHalfW;
        float barRight = barHalfW;
        float barBottom = barCenterY - barHalfH;
        float barTop = barCenterY + barHalfH;

        float handleGap = barHalfW * 0.20F;
        float handleSide = barHalfH * 1.3F;
        float resizeLeft = barRight + handleGap;
        float resizeRight = resizeLeft + handleSide * 2.0F;
        float resizeBottom = barCenterY - handleSide;
        float resizeTop = barCenterY + handleSide;

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glPushMatrix();

        try {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
            GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
            GLStateManager.glDepthMask(true);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // 6-DoF Transform into keyboard pose
            GLStateManager.glTranslatef(kbPos.x, kbPos.y, kbPos.z);
            AxisAngle4f aa = new AxisAngle4f(kbRot);
            if (aa.angle != 0.0F && (aa.x != 0.0F || aa.y != 0.0F || aa.z != 0.0F)) {
                GLStateManager.glRotatef((float) Math.toDegrees(aa.angle), aa.x, aa.y, aa.z);
            }

            // 1. Textured Keyboard Quad
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            float uMax = (float) (KB_PIXEL_W * RENDER_SCALE) / (float) TEX_W;
            float vMax = (float) (KB_PIXEL_H * RENDER_SCALE) / (float) TEX_H;

            GLStateManager.glBegin(GL11.GL_QUADS);
            GLStateManager.glTexCoord2f(0.0F, 1.0F);        GLStateManager.glVertex3f(-halfW,  halfH, 0.0F);
            GLStateManager.glTexCoord2f(uMax, 1.0F);        GLStateManager.glVertex3f( halfW,  halfH, 0.0F);
            GLStateManager.glTexCoord2f(uMax, 1.0F - vMax); GLStateManager.glVertex3f( halfW, -halfH, 0.0F);
            GLStateManager.glTexCoord2f(0.0F, 1.0F - vMax); GLStateManager.glVertex3f(-halfW, -halfH, 0.0F);
            GLStateManager.glEnd();

            // 2. Visor 1:1 Drag Bar & Resize Handle (disabled in Main Menu)
            boolean allowHandles = (mc != null && mc.theWorld != null);
            if (allowHandles && (isDragging || isResizing || hoveredHandle != KeyboardHandle.NONE)) {
                GLStateManager.glDisable(GL11.GL_TEXTURE_2D);

                // Visor 1:1 Handle Colors
                // Drag bar
                if (isDragging) {
                    GLStateManager.glColor4f(0.863F, 0.863F, 0.863F, 0.431F);
                } else if (hoveredHandle == KeyboardHandle.DRAG_BAR) {
                    GLStateManager.glColor4f(0.863F, 0.863F, 0.863F, 0.470F);
                } else {
                    GLStateManager.glColor4f(0.745F, 0.745F, 0.745F, 0.333F);
                }
                GLStateManager.glBegin(GL11.GL_QUADS);
                GLStateManager.glVertex3f(barLeft, barBottom, 0.001F);
                GLStateManager.glVertex3f(barRight, barBottom, 0.001F);
                GLStateManager.glVertex3f(barRight, barTop, 0.001F);
                GLStateManager.glVertex3f(barLeft, barTop, 0.001F);
                GLStateManager.glEnd();

                // Resize square
                if (isResizing) {
                    GLStateManager.glColor4f(0.470F, 0.863F, 1.000F, 0.627F);
                } else if (hoveredHandle == KeyboardHandle.RESIZE_HANDLE) {
                    GLStateManager.glColor4f(0.863F, 0.863F, 0.863F, 0.470F);
                } else {
                    GLStateManager.glColor4f(0.745F, 0.745F, 0.745F, 0.333F);
                }
                GLStateManager.glBegin(GL11.GL_QUADS);
                GLStateManager.glVertex3f(resizeLeft, resizeBottom, 0.001F);
                GLStateManager.glVertex3f(resizeRight, resizeBottom, 0.001F);
                GLStateManager.glVertex3f(resizeRight, resizeTop, 0.001F);
                GLStateManager.glVertex3f(resizeLeft, resizeTop, 0.001F);
                GLStateManager.glEnd();

                // Resizing outline (all 4 edges, Visor 1:1)
                if (isResizing) {
                    GLStateManager.glColor4f(0.470F, 0.863F, 1.000F, 0.627F);
                    float thick = Math.max(0.004F, halfW * 0.012F);
                    GLStateManager.glBegin(GL11.GL_QUADS);
                    // Top edge
                    GLStateManager.glVertex3f(-halfW, halfH - thick, 0.002F);
                    GLStateManager.glVertex3f( halfW, halfH - thick, 0.002F);
                    GLStateManager.glVertex3f( halfW, halfH,         0.002F);
                    GLStateManager.glVertex3f(-halfW, halfH,         0.002F);
                    // Bottom edge
                    GLStateManager.glVertex3f(-halfW, -halfH,         0.002F);
                    GLStateManager.glVertex3f( halfW, -halfH,         0.002F);
                    GLStateManager.glVertex3f( halfW, -halfH + thick, 0.002F);
                    GLStateManager.glVertex3f(-halfW, -halfH + thick, 0.002F);
                    // Left edge
                    GLStateManager.glVertex3f(-halfW,         -halfH + thick, 0.002F);
                    GLStateManager.glVertex3f(-halfW + thick, -halfH + thick, 0.002F);
                    GLStateManager.glVertex3f(-halfW + thick,  halfH - thick, 0.002F);
                    GLStateManager.glVertex3f(-halfW,          halfH - thick, 0.002F);
                    // Right edge
                    GLStateManager.glVertex3f(halfW - thick, -halfH + thick, 0.002F);
                    GLStateManager.glVertex3f(halfW,         -halfH + thick, 0.002F);
                    GLStateManager.glVertex3f(halfW,          halfH - thick, 0.002F);
                    GLStateManager.glVertex3f(halfW - thick,  halfH - thick, 0.002F);
                    GLStateManager.glEnd();
                }
            }

            // 3. Laser hit dots for both hands
            // Left pointer reticle dot (Amber / Orange: #FFA61A, 1.0F, 0.65F, 0.10F)
            if (leftPointer.hit && leftPointer.handle == KeyboardHandle.KEYS && leftPointer.hitUV.x >= 0.0F && leftPointer.hitUV.y >= 0.0F) {
                float curX = -halfW + leftPointer.hitUV.x * kbWidth;
                float curY = halfH - leftPointer.hitUV.y * kbHeight;
                float r = 0.009F;

                GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
                GLStateManager.glColor4f(1.0F, 0.65F, 0.10F, 0.95F);
                GLStateManager.glBegin(GL11.GL_TRIANGLE_FAN);
                GLStateManager.glVertex3f(curX, curY, 0.003F);
                for (int i = 0; i <= 16; i++) {
                    double angle = i * (Math.PI * 2.0 / 16.0);
                    GLStateManager.glVertex3f(curX + (float) Math.cos(angle) * r, curY + (float) Math.sin(angle) * r, 0.003F);
                }
                GLStateManager.glEnd();
            }

            // Right pointer reticle dot (Cyan / Blue: #26D9FF, 0.15F, 0.85F, 1.0F)
            if (rightPointer.hit && rightPointer.handle == KeyboardHandle.KEYS && rightPointer.hitUV.x >= 0.0F && rightPointer.hitUV.y >= 0.0F) {
                float curX = -halfW + rightPointer.hitUV.x * kbWidth;
                float curY = halfH - rightPointer.hitUV.y * kbHeight;
                float r = 0.009F;

                GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
                GLStateManager.glColor4f(0.15F, 0.85F, 1.0F, 0.95F);
                GLStateManager.glBegin(GL11.GL_TRIANGLE_FAN);
                GLStateManager.glVertex3f(curX, curY, 0.003F);
                for (int i = 0; i <= 16; i++) {
                    double angle = i * (Math.PI * 2.0 / 16.0);
                    GLStateManager.glVertex3f(curX + (float) Math.cos(angle) * r, curY + (float) Math.sin(angle) * r, 0.003F);
                }
                GLStateManager.glEnd();
            }

        } finally {
            GLStateManager.glPopMatrix();
            GLStateManager.glPopAttrib();
        }
    }

    public void destroy() {
        if (fbo != 0) {
            GLStateManager.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (textureId != 0) {
            GLStateManager.glDeleteTextures(textureId);
            textureId = 0;
        }
        if (depthRb != 0) {
            GLStateManager.glDeleteRenderbuffers(depthRb);
            depthRb = 0;
        }
    }
}
