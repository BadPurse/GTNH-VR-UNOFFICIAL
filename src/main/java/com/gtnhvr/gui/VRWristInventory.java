package com.gtnhvr.gui;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.gameplay.tasks.VRTaskBow;
import com.gtnhvr.settings.VROverlayConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.util.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Player inventory on the back of the off hand, a port of Visor Essentials' VROverlayInventory /
 * VRInvScreen. Turn the wrist so the back of the hand faces your eyes and the panel appears on the
 * forearm: armor column and player preview on the left, the nine hotbar slots as a 3x3 block (the
 * same order as the radial hotbar), the 2x2 crafting grid with its result and the 27 inventory slots
 * below. Point with the other hand: trigger = click, A = right click, off hand trigger = shift click.
 *
 * It is an overlay, not a GUI screen: the world keeps running, the HUD stays and the sticks keep
 * moving and turning you. The radial hotbar is independent of it and opens only with the grips.
 */
@Lwjgl3Aware
public final class VRWristInventory {
    private static final VRWristInventory INSTANCE = new VRWristInventory();

    // Canvas in GUI pixels; the Essentials image (258x156) is centred in it so tooltips fit around it.
    private static final int PANEL_W = 258;
    private static final int PANEL_H = 156;
    private static final int CANVAS_W = 300;
    private static final int CANVAS_H = 220;
    private static final int PANEL_X = (CANVAS_W - PANEL_W) / 2;
    private static final int PANEL_Y = (CANVAS_H - PANEL_H) / 2;
    private static final int FBO_W = CANVAS_W * 3;
    private static final int FBO_H = CANVAS_H * 3;

    /** Metres per canvas pixel at overlay scale 1.0 (scale 0.5 = Visor default gives a 0.49 m wide panel). */
    private static final float PIXEL_METERS_PER_SCALE = 0.0038F;

    // Visor OverlayOptionsPose offset of the inventory overlay, in the controller frame
    private static final float OFFSET_SIDE = 0.07F;
    private static final float OFFSET_DOWN = -0.081F;
    private static final float OFFSET_BACK = 0.20F;

    private static final float TRIGGER_PRESS = 0.60F;

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("gtnhvr", "textures/gui/wrist/inventory.png");
    private static final ResourceLocation ICONS_TEX =
            new ResourceLocation("textures/gui/icons.png");
    private static final RenderItem ITEM_RENDER = new RenderItem();

    /** Item positions inside the 258x156 image; hotbar slots follow the radial order 0..8. */
    private static final int[][] HOTBAR_POS = {
            {121, 38}, {121, 11}, {148, 11}, {148, 38}, {148, 65}, {121, 65}, {94, 65}, {94, 38}, {94, 11}};

    private boolean active = false;
    private boolean primaryWasDown = false;
    private boolean secondaryWasDown = false;
    private boolean shiftWasDown = false;
    private int lastHoverSlot = -1;

    // Drag-and-drop / Split-drag state machine (1:1 Minecraft GuiContainer & Visor)
    private boolean isDragging = false;
    private int dragMouseButton = -1; // 0 = left drag (split evenly), 1 = right drag (1 per slot)
    private final java.util.Set<Slot> draggedSlots = new java.util.LinkedHashSet<>();
    private long lastClickTime = 0L;
    private int lastClickButton = -1;
    private Slot lastClickSlot = null;

    private final Vector3f center = new Vector3f();
    private final Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);
    private final Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
    private final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
    private float pixelMeters = PIXEL_METERS_PER_SCALE * 0.5F;

    private boolean hoverValid = false;
    private float hoverX = 0.0F;
    private float hoverY = 0.0F;
    private Slot hoverSlot = null;
    private final Vector3f lastAimHitPoint = new Vector3f();

    private int fbo = 0;
    private int texture = 0;
    private int depthRb = 0;

    private VRWristInventory() {}

    public static VRWristInventory getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isHoverValid() {
        return active && hoverValid;
    }

    public boolean isWristLeft() {
        com.gtnhvr.settings.VROverlayConfig.Entry cfg = com.gtnhvr.settings.VROverlayConfig.get(com.gtnhvr.settings.VROverlayConfig.WRIST);
        return !"MAIN_HAND".equals(cfg.anchor);
    }

    public Vector3f getAimHitPoint() {
        return lastAimHitPoint;
    }

    // ------------------------------------------------------------------ layout

    /** Position of a ContainerPlayer slot inside the image, or false for slots the panel does not show. */
    private static boolean layout(int slotNumber, int[] out) {
        if (slotNumber == 0) {                         // crafting result
            out[0] = 237;
            out[1] = 36;
        } else if (slotNumber <= 4) {                  // 2x2 crafting grid
            int index = slotNumber - 1;
            out[0] = 181 + (index % 2) * 18;
            out[1] = 26 + (index / 2) * 18;
        } else if (slotNumber <= 8) {                  // armor: helmet at the top, boots at the bottom
            out[0] = 8;
            out[1] = 8 + (slotNumber - 5) * 18;
        } else if (slotNumber <= 35) {                 // 9x3 inventory
            int index = slotNumber - 9;
            out[0] = 49 + (index % 9) * 18;
            out[1] = 96 + (index / 9) * 18;
        } else if (slotNumber <= 44) {                 // hotbar as 3x3 block
            int[] pos = HOTBAR_POS[slotNumber - 36];
            out[0] = pos[0];
            out[1] = pos[1];
        } else {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ logic

    private void hide(Minecraft mc) {
        if (active) {
            active = false;
            returnCursorStack(mc);
            if (mc != null && mc.thePlayer != null) {
                try {
                    mc.thePlayer.openContainer = mc.thePlayer.inventoryContainer;
                    mc.thePlayer.inventoryContainer.windowId = 0;
                } catch (Throwable ignored) {}
            }
        }
        hoverValid = false;
        hoverSlot = null;
        primaryWasDown = false;
        secondaryWasDown = false;
        shiftWasDown = false;
        lastHoverSlot = -1;
        isDragging = false;
        draggedSlots.clear();
        lastClickTime = 0L;
        lastClickSlot = null;
        lastClickButton = -1;
    }

    /**
     * Synchronizes container 0 with server upon wrist activation to ensure zero click delay and no desync.
     */
    private void syncWithServer(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.playerController == null) {
            return;
        }
        try {
            if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
                mc.thePlayer.closeScreen();
            }
            mc.thePlayer.openContainer = mc.thePlayer.inventoryContainer;
            mc.thePlayer.openContainer.windowId = 0;
            mc.thePlayer.inventoryContainer.windowId = 0;
        } catch (Throwable ignored) {}
    }

    /** A stack left on the cursor goes back into the inventory (or is dropped) when the panel hides. */
    private void returnCursorStack(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.playerController == null) {
            return;
        }
        if (mc.thePlayer.inventory.getItemStack() == null) {
            return;
        }
        if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
            mc.thePlayer.openContainer = mc.thePlayer.inventoryContainer;
        }
        mc.thePlayer.openContainer.windowId = 0;
        Container container = mc.thePlayer.inventoryContainer;
        int targetSlotNumber = -1;
        for (Object o : container.inventorySlots) {
            Slot slot = (Slot) o;
            if (slot.inventory == mc.thePlayer.inventory && !slot.getHasStack()) {
                targetSlotNumber = slot.slotNumber;
                break;
            }
        }
        if (targetSlotNumber >= 0) {
            mc.playerController.windowClick(0, targetSlotNumber, 0, 0, mc.thePlayer);
        } else {
            mc.playerController.windowClick(0, -999, 0, 0, mc.thePlayer);
        }
    }

    private boolean planeHit(Vector3f origin, Vector3f direction, float[] out) {
        float denominator = normal.dot(direction);
        if (Math.abs(denominator) < 1.0E-4F) {
            return false;
        }
        float t = normal.dot(new Vector3f(center).sub(origin)) / denominator;
        if (t <= 0.0F) {
            return false;
        }
        Vector3f local = new Vector3f(direction).mul(t).add(origin).sub(center);
        out[0] = CANVAS_W * 0.5F + local.dot(right) / pixelMeters;
        out[1] = CANVAS_H * 0.5F - local.dot(up) / pixelMeters;
        out[2] = t;
        return true;
    }

    private static boolean insidePanel(float x, float y, float margin) {
        return x >= PANEL_X - PANEL_W * margin && x <= PANEL_X + PANEL_W * (1.0F + margin)
                && y >= PANEL_Y - PANEL_H * margin && y <= PANEL_Y + PANEL_H * (1.0F + margin);
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.WRIST);
        XrPosef head = OpenXRRuntime.getHeadPose();
        boolean wristIsLeft = !"MAIN_HAND".equals(cfg.anchor);
        boolean wristTracked = wristIsLeft ? handler.isLeftHandTracked() : handler.isRightHandTracked();
        if (!cfg.visible || mc == null || mc.thePlayer == null || mc.currentScreen != null || head == null
                || !wristTracked
                || VRTaskBow.getInstance().isDrawing()) {
            hide(mc);
            return;
        }

        // OFFHAND (default): the left wrist carries the panel and the right hand points.
        Vector3f wristPos = wristIsLeft ? handler.getLeftHandPos() : handler.getRightHandPos();
        Quaternionf wristRot = wristIsLeft ? handler.getLeftHandRot() : handler.getRightHandRot();
        Vector3f pointerPos = wristIsLeft ? handler.getRightHandPos() : handler.getLeftHandPos();
        Quaternionf pointerRot = wristIsLeft ? handler.getRightHandRot() : handler.getLeftHandRot();

        Vector3f hmdPos = new Vector3f(head.position$().x(), head.position$().y(), head.position$().z());
        Quaternionf hmdRot = new Quaternionf(head.orientation().x(), head.orientation().y(),
                head.orientation().z(), head.orientation().w());
        Vector3f hmdForward = hmdRot.transform(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());

        // 1:1 Visor Essentials pose anchor (PoseAnchor.OFFHAND / defaults_hand_hud.yml):
        // Position offset: (-0.07, -0.081, 0.20) for left hand, (+0.07, -0.081, 0.20) for right hand
        // Rotation offset: Roll 180 deg (PI), Yaw +90 deg (+PI/2) for left hand, -90 deg (-PI/2) for right hand
        pixelMeters = PIXEL_METERS_PER_SCALE * cfg.scale;
        Vector3f posOffset = new Vector3f(
                wristIsLeft ? -0.07F : 0.07F,
                -0.081F,
                0.20F
        );
        Quaternionf rotOffset = new Quaternionf().rotationZYX(
                (float) Math.PI,
                wristIsLeft ? (float) (Math.PI / 2.0) : -(float) (Math.PI / 2.0),
                0.0F
        );
        Quaternionf panelRot = new Quaternionf(wristRot).mul(rotOffset);

        panelRot.transform(new Vector3f(1.0F, 0.0F, 0.0F), right);
        panelRot.transform(new Vector3f(0.0F, 1.0F, 0.0F), up);
        panelRot.transform(new Vector3f(0.0F, 0.0F, 1.0F), normal);

        center.set(wristPos).add(wristRot.transform(posOffset, new Vector3f()))
                .add(new Vector3f(right).mul(cfg.offsetX))
                .add(new Vector3f(up).mul(cfg.offsetY));

        // Natural wrist-turn activation:
        // Panel appears purely by turning the wrist towards the eyes and glancing at it.
        // No Grip or button press required!
        Vector3f toHead = new Vector3f(hmdPos).sub(center);
        float distance = toHead.length();
        if (distance < 0.05F || distance > (active ? 1.20F : 0.95F)) {
            hide(mc);
            return;
        }
        toHead.div(distance);
        boolean wasActive = active;

        // Facing test: panel normal points towards the player's eyes
        float facingDot = normal.dot(toHead);
        boolean facing = facingDot > (wasActive ? 0.10F : 0.25F);

        // General glance direction: head faces generally towards the hand
        Vector3f toHand = new Vector3f(center).sub(hmdPos).normalize();
        float glanceDot = hmdForward.dot(toHand);
        boolean glancing = glanceDot > (wasActive ? 0.10F : 0.20F);

        boolean otherTracked = wristIsLeft ? handler.isRightHandTracked() : handler.isLeftHandTracked();
        boolean aimHit = false;
        boolean aimed = false;
        float[] aim = new float[3];
        if (otherTracked) {
            Vector3f pointerDir = pointerRot.transform(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());
            aimHit = planeHit(pointerPos, pointerDir, aim) && aim[2] < 1.5F;
            aimed = aimHit && insidePanel(aim[0], aim[1], 0.30F);
            if (aimHit) {
                lastAimHitPoint.set(pointerPos).add(new Vector3f(pointerDir).mul(aim[2]));
            }
        }

        if (!(facing && (glancing || aimed))) {
            hide(mc);
            return;
        }

        float pointerTrigger = otherTracked ? (wristIsLeft ? handler.getRightTriggerValue() : handler.getLeftTriggerValue()) : 0.0F;
        boolean secondary = otherTracked && (wristIsLeft ? handler.isRightPrimaryPressed() : handler.isLeftPrimaryPressed());
        float shiftTrigger = wristIsLeft ? handler.getLeftTriggerValue() : handler.getRightTriggerValue();
        boolean shiftButton = wristIsLeft ? handler.isLeftPrimaryPressed() : handler.isRightPrimaryPressed();
        boolean primaryDown = pointerTrigger > TRIGGER_PRESS;
        boolean secondaryDown = secondary;
        boolean shiftDown = (shiftTrigger > TRIGGER_PRESS) || shiftButton;

        if (!active) {
            active = true;
            // buttons already held when the panel appears must not click
            primaryWasDown = primaryDown;
            secondaryWasDown = secondaryDown;
            shiftWasDown = shiftDown;
            handler.triggerHaptic(wristIsLeft, 8_000_000L, 0.30F, 0.0F);
            syncWithServer(mc);
        }

        // Close any dangling external container so player.inventoryContainer is guaranteed
        if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
            mc.thePlayer.closeScreen();
        }
        mc.thePlayer.openContainer = mc.thePlayer.inventoryContainer;
        mc.thePlayer.openContainer.windowId = 0;
        Container container = mc.thePlayer.inventoryContainer;

        // Hovered slot under the pointer
        hoverValid = aimed;
        hoverX = aim[0];
        hoverY = aim[1];
        hoverSlot = null;
        if (hoverValid) {
            int[] pos = new int[2];
            for (Object o : container.inventorySlots) {
                Slot slot = (Slot) o;
                if (!layout(slot.slotNumber, pos)) {
                    continue;
                }
                float sx = PANEL_X + pos[0];
                float sy = PANEL_Y + pos[1];
                if (hoverX >= sx - 1 && hoverX < sx + 17 && hoverY >= sy - 1 && hoverY < sy + 17) {
                    hoverSlot = slot;
                    break;
                }
            }
        }
        int hoverNumber = hoverSlot != null ? hoverSlot.slotNumber : -1;
        if (hoverNumber != lastHoverSlot) {
            lastHoverSlot = hoverNumber;
            if (hoverNumber >= 0) {
                handler.triggerHaptic(!wristIsLeft, 2_000_000L, 0.20F, 0.0F);
            }
        }

        // --- 1:1 VISOR CONTAINER INTERACTION & DRAG STATE MACHINE ---
        boolean primaryPressEdge = primaryDown && !primaryWasDown;
        boolean secondaryPressEdge = secondaryDown && !secondaryWasDown;
        int clickButton = primaryPressEdge ? 0 : (secondaryPressEdge ? 1 : -1);

        if (clickButton != -1 && hoverValid) {
            handleInitialClick(mc, handler, !wristIsLeft, container, clickButton, shiftDown);
        } else if (isDragging) {
            boolean isButtonStillHeld = (dragMouseButton == 0 && primaryDown) || (dragMouseButton == 1 && secondaryDown);
            if (isButtonStillHeld) {
                // Update drag path while pointer moves across slots
                ItemStack cursorStack = mc.thePlayer.inventory.getItemStack();
                if (cursorStack != null && hoverSlot != null && canDragIntoSlot(hoverSlot, cursorStack)) {
                    if (draggedSlots.add(hoverSlot)) {
                        handler.triggerHaptic(!wristIsLeft, 2_000_000L, 0.25F, 0.0F);
                    }
                }
            } else {
                // Button released -> finalize drag or place item!
                handleDragRelease(mc, handler, !wristIsLeft, container);
            }
        }

        primaryWasDown = primaryDown;
        secondaryWasDown = secondaryDown;
        shiftWasDown = shiftDown;
    }

    private void handleInitialClick(Minecraft mc, VRControllerHandler handler, boolean pointerIsLeft, Container container, int button, boolean shiftDown) {
        if (mc.playerController == null || mc.thePlayer == null) return;
        if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
            mc.thePlayer.openContainer = mc.thePlayer.inventoryContainer;
        }
        mc.thePlayer.openContainer.windowId = 0;
        ItemStack cursorStack = mc.thePlayer.inventory.getItemStack();
        long now = Minecraft.getSystemTime();

        // 1. Double-click detection (same slot, same button within 250ms, with held item)
        boolean isDoubleClick = (hoverSlot != null && lastClickSlot == hoverSlot
                && lastClickButton == button && (now - lastClickTime < 250L)
                && cursorStack != null);

        if (isDoubleClick) {
            // ClickType 6: Collect matching items into held stack
            mc.playerController.windowClick(0, hoverSlot.slotNumber, button, 6, mc.thePlayer);
            handler.triggerHaptic(pointerIsLeft, 8_000_000L, 0.50F, 0.0F);
            isDragging = false;
            draggedSlots.clear();
            lastClickTime = 0L;
            lastClickSlot = null;
            return;
        }

        // 2. Shift-click (quick-transfer)
        if (shiftDown && hoverSlot != null) {
            // ClickType 1: Quick-transfer
            mc.playerController.windowClick(0, hoverSlot.slotNumber, button, 1, mc.thePlayer);
            handler.triggerHaptic(pointerIsLeft, 6_000_000L, 0.45F, 0.0F);
            lastClickTime = now;
            lastClickButton = button;
            lastClickSlot = hoverSlot;
            isDragging = false;
            draggedSlots.clear();
            if (hoverSlot.slotNumber >= 1 && hoverSlot.slotNumber <= 4 && container instanceof ContainerPlayer cp) {
                cp.onCraftMatrixChanged(cp.craftMatrix);
            }
            return;
        }

        // 3. Cursor is empty: pickup item stack or half stack
        if (cursorStack == null) {
            if (hoverSlot != null) {
                // ClickType 0: button 0 = take all, button 1 = take half
                mc.playerController.windowClick(0, hoverSlot.slotNumber, button, 0, mc.thePlayer);
                handler.triggerHaptic(pointerIsLeft, 6_000_000L, 0.45F, 0.0F);
                lastClickTime = now;
                lastClickButton = button;
                lastClickSlot = hoverSlot;
                if (hoverSlot.slotNumber >= 1 && hoverSlot.slotNumber <= 4 && container instanceof ContainerPlayer cp) {
                    cp.onCraftMatrixChanged(cp.craftMatrix);
                }
            }
            isDragging = false;
            draggedSlots.clear();
            return;
        }

        // 4. Cursor has an item: initiate drag or placement
        isDragging = true;
        dragMouseButton = button;
        draggedSlots.clear();
        if (hoverSlot != null && canDragIntoSlot(hoverSlot, cursorStack)) {
            draggedSlots.add(hoverSlot);
        }
        lastClickTime = now;
        lastClickButton = button;
        lastClickSlot = hoverSlot;
    }

    private void handleDragRelease(Minecraft mc, VRControllerHandler handler, boolean pointerIsLeft, Container container) {
        isDragging = false;
        if (mc.playerController == null || mc.thePlayer == null) {
            draggedSlots.clear();
            return;
        }
        if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
            mc.thePlayer.openContainer = mc.thePlayer.inventoryContainer;
        }
        mc.thePlayer.openContainer.windowId = 0;
        ItemStack cursorStack = mc.thePlayer.inventory.getItemStack();
        if (cursorStack == null) {
            draggedSlots.clear();
            return;
        }

        if (draggedSlots.size() > 1) {
            // Multi-slot drag: execute vanilla Minecraft Container drag split!
            int dragType = (dragMouseButton == 1) ? 1 : 0;
            // Header
            mc.playerController.windowClick(0, -999, Container.func_94534_d(0, dragType), 5, mc.thePlayer);
            // Slots
            for (Slot slot : draggedSlots) {
                mc.playerController.windowClick(0, slot.slotNumber, Container.func_94534_d(1, dragType), 5, mc.thePlayer);
            }
            // Footer
            mc.playerController.windowClick(0, -999, Container.func_94534_d(2, dragType), 5, mc.thePlayer);
            handler.triggerHaptic(pointerIsLeft, 6_000_000L, 0.45F, 0.0F);
        } else {
            // Single slot click or drop outside!
            if (hoverSlot != null) {
                // ClickType 0: button 0 = place all / swap; button 1 = place 1 item
                mc.playerController.windowClick(0, hoverSlot.slotNumber, dragMouseButton, 0, mc.thePlayer);
                handler.triggerHaptic(pointerIsLeft, 6_000_000L, 0.45F, 0.0F);
                if (hoverSlot.slotNumber >= 1 && hoverSlot.slotNumber <= 4 && container instanceof ContainerPlayer cp) {
                    cp.onCraftMatrixChanged(cp.craftMatrix);
                }
            } else if (!insidePanel(hoverX, hoverY, 0.0F)) {
                // Clicked outside panel while holding item -> throw/drop item in world!
                // button 0 = throw full stack, button 1 = throw 1 item
                mc.playerController.windowClick(0, -999, dragMouseButton, 0, mc.thePlayer);
                handler.triggerHaptic(pointerIsLeft, 6_000_000L, 0.40F, 0.0F);
            }
        }
        draggedSlots.clear();
    }

    private static boolean canDragIntoSlot(Slot slot, ItemStack stack) {
        if (slot == null || stack == null) return false;
        if (!slot.isItemValid(stack)) return false;
        if (slot.slotNumber == 0) return false; // Crafting result slot is take-only
        ItemStack existing = slot.getStack();
        if (existing == null) return true;
        return existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)
                && existing.stackSize < existing.getMaxStackSize()
                && existing.stackSize < slot.getSlotStackLimit();
    }

    // ------------------------------------------------------------------ rendering

    private void ensureTarget() {
        if (fbo != 0) {
            return;
        }
        fbo = GL30.glGenFramebuffers();
        texture = GL11.glGenTextures();
        depthRb = GL30.glGenRenderbuffers();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, FBO_W, FBO_H, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRb);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, FBO_W, FBO_H);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRb);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /** Draws the panel into its texture once per frame, before the eye passes. */
    public void prepareTextures(Minecraft mc) {
        if (!active || mc == null || mc.thePlayer == null) {
            return;
        }
        ensureTarget();

        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GL11.glPushAttrib(GL11.GL_VIEWPORT_BIT);
        VRHotbarRenderer.markRendering(true);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL11.glViewport(0, 0, FBO_W, FBO_H);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, CANVAS_W, CANVAS_H, 0.0D, 1000.0D, 3000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                drawPanel(mc);
            } finally {
                RenderHelper.disableStandardItemLighting();
                GL11.glPopAttrib();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
        } finally {
            VRHotbarRenderer.markRendering(false);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GL11.glPopAttrib();
            if (scissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private void drawPanel(Minecraft mc) {
        EntityPlayer player = mc.thePlayer;
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        // Player preview in the left frame
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GuiInventory.func_147046_a(PANEL_X + 51, PANEL_Y + 75, 30, 0.0F, -20.0F, player);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);

        // Slots
        int slotMain = (player.inventory != null) ? player.inventory.currentItem : 0;
        int slotOffhand = VRHotbarSelector.getInstance().getOffhandSlot();

        int[] pos = new int[2];
        for (Object o : player.inventoryContainer.inventorySlots) {
            Slot slot = (Slot) o;
            if (!layout(slot.slotNumber, pos)) {
                continue;
            }
            int x = PANEL_X + pos[0];
            int y = PANEL_Y + pos[1];

            // Hotbar slots (ContainerPlayer slots 36..44) main/offhand highlights
            if (slot.slotNumber >= 36 && slot.slotNumber <= 44) {
                int hotbarIdx = slot.slotNumber - 36;
                boolean isMain = (hotbarIdx == slotMain);
                boolean isOffhand = (hotbarIdx == slotOffhand && slotOffhand >= 0 && slotOffhand < 9);

                if (isMain) {
                    // Match the radial hotbar: textured cyan selection, no colored square placeholder.
                    drawTexturedSlotSelection(mc, x, y, hotbarIdx == 0, false);
                } else if (isOffhand) {
                    // Match the radial hotbar: textured orange selection, no colored square placeholder.
                    drawTexturedSlotSelection(mc, x, y, hotbarIdx == 0, true);
                }
            }

            if (slot == hoverSlot && (!isDragging || !draggedSlots.contains(slot))) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.50F);
                drawRect(x, y, x + 16, y + 16);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }

            // Render slot item or real-time drag preview item (no blue background glow!)
            ItemStack stack = slot.getStack();
            if (isDragging && draggedSlots.contains(slot)) {
                ItemStack cursor = player.inventory.getItemStack();
                if (cursor != null) {
                    int dragCount = Math.max(1, draggedSlots.size());
                    int addCount = (dragMouseButton == 0) ? (cursor.stackSize / dragCount) : 1;
                    if (addCount > 0) {
                        stack = cursor.copy();
                        int existing = slot.getHasStack() ? slot.getStack().stackSize : 0;
                        stack.stackSize = Math.min(stack.getMaxStackSize(), existing + addCount);
                    }
                }
            }
            if (stack != null) {
                drawItem(mc, stack, x, y);
            }
        }

        // Held item on cursor and tooltip
        ItemStack held = player.inventory.getItemStack();
        if (hoverValid) {
            if (held != null) {
                ItemStack cursorPreview = held;
                if (isDragging && !draggedSlots.isEmpty()) {
                    int dragCount = Math.max(1, draggedSlots.size());
                    int perSlot = (dragMouseButton == 0) ? (held.stackSize / dragCount) : 1;
                    int remainder = (dragMouseButton == 0) ? (held.stackSize % dragCount) : Math.max(0, held.stackSize - dragCount);
                    if (remainder > 0) {
                        cursorPreview = held.copy();
                        cursorPreview.stackSize = remainder;
                    } else {
                        cursorPreview = null;
                    }
                }
                if (cursorPreview != null) {
                    drawItem(mc, cursorPreview, Math.round(hoverX) - 8, Math.round(hoverY) - 8);
                }
            } else if (hoverSlot != null && hoverSlot.getHasStack()) {
                drawTooltip(mc, hoverSlot.getStack(), Math.round(hoverX), Math.round(hoverY));
            }
        }
    }

    private static void drawItem(Minecraft mc, ItemStack stack, int x, int y) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        ITEM_RENDER.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);
    }

    private static void drawTooltip(Minecraft mc, ItemStack stack, int mouseX, int mouseY) {
        FontRenderer font = mc.fontRenderer;
        @SuppressWarnings("unchecked")
        List<String> source = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        if (source == null || source.isEmpty() || font == null) {
            return;
        }
        List<String> lines = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            lines.add(i == 0 ? stack.getRarity().rarityColor + source.get(i) : "\u00a77" + source.get(i));
        }

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }
        int height = 8 + (lines.size() > 1 ? 2 + (lines.size() - 1) * 10 : 0);

        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + width + 6 > CANVAS_W) {
            x = Math.max(4, mouseX - width - 14);
        }
        if (y + height + 6 > CANVAS_H) {
            y = CANVAS_H - height - 6;
        }
        if (y < 4) {
            y = 4;
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.063F, 0.0F, 0.063F, 0.94F);
        drawRect(x - 3, y - 3, x + width + 3, y + height + 3);
        GL11.glColor4f(0.31F, 0.0F, 1.0F, 0.66F);
        drawRect(x - 3, y - 3, x + width + 3, y - 2);
        drawRect(x - 3, y + height + 2, x + width + 3, y + height + 3);
        drawRect(x - 3, y - 3, x - 2, y + height + 3);
        drawRect(x + width + 2, y - 3, x + width + 3, y + height + 3);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        int lineY = y;
        for (int i = 0; i < lines.size(); i++) {
            font.drawStringWithShadow(lines.get(i), x, lineY, 0xFFFFFF);
            lineY += (i == 0) ? 12 : 10;
        }
    }

    private static void drawTexturedSlotSelection(Minecraft mc, float x, float y, boolean centerSlot, boolean offhand) {
        String textureName;
        if (offhand) {
            textureName = centerSlot ? "hotbar_main_selected0.png" : "hotbar_main_selected1.png";
            GL11.glColor4f(1.0F, 0.65F, 0.10F, 1.0F);
        } else {
            textureName = centerSlot ? "hotbar_offhand_selected0.png" : "hotbar_offhand_selected1.png";
            GL11.glColor4f(0.15F, 0.85F, 1.0F, 1.0F);
        }
        mc.getTextureManager().bindTexture(new ResourceLocation(
                "gtnhvr", "textures/gui/overlays/hotbar/" + textureName));
        drawTexturedRect(x - 1.0F, y - 1.0F, 18.0F, 18.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawTexturedRect(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(1.0F, 0.0F); GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(1.0F, 1.0F); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(0.0F, 1.0F); GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private static void drawRect(float x0, float y0, float x1, float y1) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x0, y1);
        GL11.glEnd();
    }

    /** Draws the panel as a floating quad inside an eye pass (room space). */
    public void render() {
        if (!active || texture == 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        float halfWidth = CANVAS_W * pixelMeters * 0.5F;
        float halfHeight = CANVAS_H * pixelMeters * 0.5F;
        Vector3f r = new Vector3f(right).mul(halfWidth);
        Vector3f u = new Vector3f(up).mul(halfHeight);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // 1. Draw Wrist Inventory Panel Quad
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex3f(center.x - r.x - u.x, center.y - r.y - u.y, center.z - r.z - u.z);
            GL11.glTexCoord2f(1.0F, 0.0F);
            GL11.glVertex3f(center.x + r.x - u.x, center.y + r.y - u.y, center.z + r.z - u.z);
            GL11.glTexCoord2f(1.0F, 1.0F);
            GL11.glVertex3f(center.x + r.x + u.x, center.y + r.y + u.y, center.z + r.z + u.z);
            GL11.glTexCoord2f(0.0F, 1.0F);
            GL11.glVertex3f(center.x - r.x + u.x, center.y - r.y + u.y, center.z - r.z + u.z);
            GL11.glEnd();

            // 2. Render Authentic Visor Small '+' Crosshair on wrist inventory panel
            if (hoverValid) {
                renderCrosshair(mc);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * Renders authentic Visor small '+' crosshair directly on the wrist inventory quad surface,
     * targeting slots with zero blur and perfect inverted contrast (1:1 with Visor HandEffectCrosshair).
     * Laser beam line is omitted for the wrist inventory panel.
     */
    private void renderCrosshair(Minecraft mc) {
        VRControllerHandler handler = VRControllerHandler.getInstance();
        boolean wristIsLeft = isWristLeft();
        boolean otherTracked = wristIsLeft ? handler.isRightHandTracked() : handler.isLeftHandTracked();
        if (!otherTracked) return;

        // Position on panel surface (offset 1.5mm along normal towards player)
        Vector3f crossPos = new Vector3f(lastAimHitPoint).add(new Vector3f(normal).mul(0.0015F));

        // Authentic Visor Small '+' Crosshair (icons.png with inverting blend mode)
        // Size: 0.009m half-size = 18mm total diameter (matches a 16x16 slot!)
        float hs = 0.009F;
        Vector3f rx = new Vector3f(right).mul(hs);
        Vector3f uy = new Vector3f(up).mul(hs);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        mc.getTextureManager().bindTexture(ICONS_TEX);

        // 1:1 Visor inverting blend mode: GL_ONE_MINUS_DST_COLOR, GL_ONE_MINUS_SRC_COLOR
        GL14.glBlendFuncSeparate(
                GL11.GL_ONE_MINUS_DST_COLOR,
                GL11.GL_ONE_MINUS_SRC_COLOR,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glBegin(GL11.GL_QUADS);
        // Bottom-left
        GL11.glTexCoord2f(0.0F, 0.0625F);
        GL11.glVertex3f(crossPos.x - rx.x - uy.x, crossPos.y - rx.y - uy.y, crossPos.z - rx.z - uy.z);
        // Bottom-right
        GL11.glTexCoord2f(0.0625F, 0.0625F);
        GL11.glVertex3f(crossPos.x + rx.x - uy.x, crossPos.y + rx.y - uy.y, crossPos.z + rx.z - uy.z);
        // Top-right
        GL11.glTexCoord2f(0.0625F, 0.0F);
        GL11.glVertex3f(crossPos.x + rx.x + uy.x, crossPos.y + rx.y + uy.y, crossPos.z + rx.z + uy.z);
        // Top-left
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex3f(crossPos.x - rx.x + uy.x, crossPos.y - rx.y + uy.y, crossPos.z - rx.z + uy.z);
        GL11.glEnd();
    }
}