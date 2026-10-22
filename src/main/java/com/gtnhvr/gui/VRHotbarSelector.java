package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.settings.VROverlayConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Radial 3x3 hotbar selector, a port of Visor's TaskHotBar, VROverlayHotBar and
 * VROverlayRadialSelector.
 *
 * Holding a side grip opens a hotbar for that hand: a 98x98 radial image appears in front of the
 * hand, facing the headset, and stays where it was opened (it only follows head translation).
 * The controller ray is intersected with the image plane; the point decides the slice:
 * centre dead zone = slot 0, then the eight sectors Top = 1, Top Right = 2, Right = 3,
 * Bottom Right = 4, Bottom = 5, Bottom Left = 6, Left = 7, Top Left = 8. Releasing the grip
 * confirms. The two hands cannot hold the same slot (handleSlotCollision).
 */
@Lwjgl3Aware
public final class VRHotbarSelector {
    public static final int NOT_SELECTED = -1;
    private static final int NULL = -2;

    private static final VRHotbarSelector INSTANCE = new VRHotbarSelector();

    /** Width of the whole 160 px canvas in metres. */
    private static final float QUAD_WIDTH = 0.40F;
    private static final float PIXEL_METERS = QUAD_WIDTH / VRHotbarRenderer.CANVAS_SIZE;
    /** Distance in front of the hand at which a hotbar opens. */
    private static final float MENU_DISTANCE = 0.55F;
    /**
     * Visor expresses the centre radius in overlay GUI pixels (slider 10..90, default 50). The
     * port converts the slider value to pixels of the 98x98 hotbar image.
     */
    private static final float IMAGE_PIXELS_PER_RADIUS_UNIT = 0.35F;
    private static final double STICKY_RADIUS_MARGIN = 8.0 * IMAGE_PIXELS_PER_RADIUS_UNIT;
    private static final double SECTOR_HALF_ANGLE = Math.PI / 8.0;

    private static final float GRIP_OPEN = 0.15F;
    private static final float GRIP_CLOSE = 0.08F;

    private final Radial mainRadial = new Radial(true);
    private final Radial offhandRadial = new Radial(false);

    private int slotMain = 0;
    private int slotOffhand = NOT_SELECTED;
    private int slotMainBack = NULL;
    private int slotOffhandBack = NULL;
    private boolean resetData = true;
    private boolean pressedMain = false;
    private boolean pressedOffhand = false;

    private VRHotbarSelector() {}

    public static VRHotbarSelector getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ per-hand radial state

    static final class Radial {
        final boolean mainHand;
        boolean active = false;
        float scale = 1.0F;
        int selectedSlice = NOT_SELECTED;
        int disabledSlice = NOT_SELECTED;
        final Vector3f center = new Vector3f();
        final Vector3f offsetFromHead = new Vector3f();
        final Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
        final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
        final Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);
        final VRHotbarRenderer renderer = new VRHotbarRenderer();

        Radial(boolean mainHand) {
            this.mainHand = mainHand;
        }

        /** Places the image in front of the hand, oriented like the headset. */
        void open(Vector3f handPos, Quaternionf handRot, Vector3f hmdPos, Quaternionf hmdRot) {
            VROverlayConfig.Entry cfg = VROverlayConfig.get(mainHand ? VROverlayConfig.HOTBAR_MAIN : VROverlayConfig.HOTBAR_OFFHAND);
            float dist = cfg.distance;
            scale = cfg.scale;
            Vector3f forward = handRot.transform(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());
            center.set(handPos).add(new Vector3f(forward).mul(dist));
            hmdRot.transform(new Vector3f(1.0F, 0.0F, 0.0F), right);
            hmdRot.transform(new Vector3f(0.0F, 1.0F, 0.0F), up);
            hmdRot.transform(new Vector3f(0.0F, 0.0F, 1.0F), normal);
            center.add(new Vector3f(right).mul(cfg.offsetX)).add(new Vector3f(up).mul(cfg.offsetY));
            offsetFromHead.set(center).sub(hmdPos);
            active = true;
        }

        void close() {
            active = false;
            disabledSlice = NOT_SELECTED;
        }

        boolean isDisabled(int slice) {
            return slice != NOT_SELECTED && slice == disabledSlice;
        }

        /** Intersects the controller ray with the image plane and updates the hovered slice. */
        void updateSelection(Vector3f handPos, Quaternionf handRot, Vector3f hmdPos) {
            center.set(hmdPos).add(offsetFromHead);

            Vector3f direction = handRot.transform(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());
            float denominator = normal.dot(direction);
            if (Math.abs(denominator) < 1.0E-4F) {
                return;
            }
            float distance = normal.dot(new Vector3f(center).sub(handPos)) / denominator;
            if (distance <= 0.0F || distance > 4.0F) {
                return;
            }
            Vector3f local = new Vector3f(direction).mul(distance).add(handPos).sub(center);
            // image space: x to the right, y downwards, origin in the middle of the image
            double x = local.dot(right) / (PIXEL_METERS * scale);
            double y = -local.dot(up) / (PIXEL_METERS * scale);

            int next = sliceFromPosition(x, y);
            if (next != NOT_SELECTED && next != selectedSlice) {
                selectedSlice = next;
                VRControllerHandler.getInstance()
                        .triggerHaptic(!mainHand, 3_000_000L, VRClientSettings.hapticStrength, 0.0F);
            }
        }

        private int sliceFromPosition(double x, double y) {
            // sticky check keeps the current slice while the pointer jitters on a border
            HotBarSlice current = HotBarSlice.fromSlot(selectedSlice);
            if (current != HotBarSlice.NOT_SELECTED && !isDisabled(selectedSlice) && contains(current, x, y, true)) {
                return selectedSlice;
            }
            for (HotBarSlice slice : HotBarSlice.values()) {
                if (slice == HotBarSlice.NOT_SELECTED) {
                    continue;
                }
                if (contains(slice, x, y, false)) {
                    return isDisabled(slice.getSlot()) ? NOT_SELECTED : slice.getSlot();
                }
            }
            return NOT_SELECTED;
        }
    }

    private static boolean contains(HotBarSlice slice, double x, double y, boolean sticky) {
        double centerRadius = VRClientSettings.hotbarCenterRadius * IMAGE_PIXELS_PER_RADIUS_UNIT;
        double radiusMargin = sticky ? STICKY_RADIUS_MARGIN : 0.0;
        double angleMargin = sticky ? Math.toRadians(VRClientSettings.hotbarHysteresisMargin) : 0.0;
        double distance = Math.sqrt(x * x + y * y);

        if (slice == HotBarSlice.CENTER) {
            return distance <= centerRadius + radiusMargin;
        }
        if (distance < centerRadius - radiusMargin) {
            return false;
        }
        double delta = wrapAngle(Math.atan2(y, x) - slice.getSectorAngle());
        return Math.abs(delta) <= SECTOR_HALF_ANGLE + angleMargin;
    }

    private static double wrapAngle(double angle) {
        angle %= 2.0 * Math.PI;
        if (angle > Math.PI) return angle - 2.0 * Math.PI;
        if (angle < -Math.PI) return angle + 2.0 * Math.PI;
        return angle;
    }

    // ------------------------------------------------------------------ TaskHotBar logic

    /**
     * Runs once per frame while the game is in the world view.
     *
     * @param rightGrip / leftGrip squeeze values of the main (right) and off (left) controllers
     */
    public void update(Minecraft mc, float rightGrip, float leftGrip,
                       Vector3f rightPos, Quaternionf rightRot, boolean rightTracked,
                       Vector3f leftPos, Quaternionf leftRot, boolean leftTracked,
                       Vector3f hmdPos, Quaternionf hmdRot) {
        if (mc == null || mc.thePlayer == null || mc.thePlayer.inventory == null) {
            closeAll();
            return;
        }
        InventoryPlayer inventory = mc.thePlayer.inventory;
        boolean twoHanded = VRClientSettings.worldTwoHanded;

        boolean inputMain = rightTracked && VROverlayConfig.get(VROverlayConfig.HOTBAR_MAIN).visible
                && rightGrip > (pressedMain ? GRIP_CLOSE : GRIP_OPEN);
        boolean inputOffhand = twoHanded && leftTracked && VROverlayConfig.get(VROverlayConfig.HOTBAR_OFFHAND).visible
                && leftGrip > (pressedOffhand ? GRIP_CLOSE : GRIP_OPEN);

        // Suppress offhand radial wheel when wrist inventory is active
        if (VRWristInventory.getInstance().isActive()) {
            inputOffhand = false;
            if (offhandRadial.active) {
                offhandRadial.close();
                pressedOffhand = false;
            }
        }

        int slotMainNew = inventory.currentItem;
        if (resetData || slotMainNew != slotMain) {
            slotMain = slotMainNew;
            resetData = false;
        }

        if (!twoHanded) {
            if (offhandRadial.active) {
                offhandRadial.close();
                pressedOffhand = false;
            }
            slotOffhand = NOT_SELECTED;
        } else {
            if (slotOffhand == NOT_SELECTED) {
                slotOffhand = (slotMain == 0) ? 1 : 0;
            } else if (slotOffhand == slotMain) {
                slotOffhand = (slotMain + 1) % 9;
            }
        }

        updateDisabledSlices();

        // ---- OFFHAND
        if (twoHanded) {
            if (inputOffhand && !pressedOffhand) {
                VRControllerHandler.getInstance().triggerHaptic(true, 10_000_000L, 0.65F, 0.0F);
                offhandRadial.open(leftPos, leftRot, anchorPosition(false, hmdPos, rightPos, leftPos), hmdRot);
                pressedOffhand = true;
            }
            if (pressedOffhand) {
                // two hotbars on the same slice: the off hand moves to slot 1
                if (mainRadial.active && mainRadial.selectedSlice == 0 && offhandRadial.selectedSlice == 0) {
                    offhandRadial.selectedSlice = 1;
                }
                offhandRadial.updateSelection(leftPos, leftRot, anchorPosition(false, hmdPos, rightPos, leftPos));
                slotOffhand = offhandRadial.selectedSlice;
                if (slotOffhand != NOT_SELECTED) {
                    if (slotMainBack != NULL && slotOffhand != slotMainBack) {
                        // switching back
                        slotMain = slotMainBack;
                        inventory.currentItem = slotMain;
                        slotMainBack = NULL;
                    } else if (slotOffhand == slotMain) {
                        // switching if collide
                        handleSlotCollision(inventory, true, true);
                    }
                }
                if (!inputOffhand) {
                    VRControllerHandler.getInstance().triggerHaptic(true, 5_000_000L, 0.40F, 0.0F);
                    offhandRadial.close();
                    pressedOffhand = false;
                    slotOffhandBack = NULL;
                    slotMainBack = NULL;
                }
            }
        }

        // ---- MAIN HAND
        if (inputMain && !pressedMain) {
            VRControllerHandler.getInstance().triggerHaptic(false, 10_000_000L, 0.65F, 0.0F);
            if (mainRadial.selectedSlice == NOT_SELECTED) {
                mainRadial.selectedSlice = inventory.currentItem;
            }
            mainRadial.open(rightPos, rightRot, anchorPosition(true, hmdPos, rightPos, leftPos), hmdRot);
            pressedMain = true;
        }
        if (pressedMain) {
            mainRadial.updateSelection(rightPos, rightRot, anchorPosition(true, hmdPos, rightPos, leftPos));
            slotMain = mainRadial.selectedSlice;
            if (slotMain != NOT_SELECTED) {
                inventory.currentItem = slotMain;

                // if selected item in offhand
                if (slotOffhandBack != NULL && slotMain != slotOffhandBack) {
                    // switching back
                    slotOffhand = slotOffhandBack;
                    slotOffhandBack = NULL;
                } else if (slotMain == slotOffhand) {
                    // switching if collide
                    handleSlotCollision(inventory, false, true);
                }
            }
            if (!inputMain) {
                VRControllerHandler.getInstance().triggerHaptic(false, 5_000_000L, 0.40F, 0.0F);
                mainRadial.close();
                pressedMain = false;
                slotOffhandBack = NULL;
                slotMainBack = NULL;
            }
        }
        if (twoHanded) {
            if (slotOffhand == slotMain) {
                slotOffhand = (slotMain + 1) % 9;
            }
        } else {
            slotOffhand = NOT_SELECTED;
        }
    }

    private static final Vector3f WORLD_ANCHOR = new Vector3f();

    /** Reference the open hotbar follows: head, one of the controllers, or nothing (fixed in the room). */
    private static Vector3f anchorPosition(boolean mainRadial, Vector3f hmdPos, Vector3f rightPos, Vector3f leftPos) {
        String anchor = VROverlayConfig.get(mainRadial ? VROverlayConfig.HOTBAR_MAIN : VROverlayConfig.HOTBAR_OFFHAND).anchor;
        switch (anchor) {
            case "MAIN_HAND": return rightPos;
            case "OFFHAND": return leftPos;
            case "WORLD": return WORLD_ANCHOR;
            default: return hmdPos;
        }
    }

    /** Each hand cannot pick the slice the other hand has selected. */
    private void updateDisabledSlices() {
        mainRadial.disabledSlice = (offhandRadial.active && offhandRadial.selectedSlice != NOT_SELECTED)
                ? offhandRadial.selectedSlice : NOT_SELECTED;
        offhandRadial.disabledSlice = (mainRadial.active && mainRadial.selectedSlice != NOT_SELECTED)
                ? mainRadial.selectedSlice : NOT_SELECTED;
    }

    private void handleSlotCollision(InventoryPlayer inventory, boolean moveMainHand, boolean switchableBack) {
        if (moveMainHand) {
            if (switchableBack) {
                slotMainBack = slotMain;
            }
            inventory.currentItem = slotOffhand == 8 ? 0 : slotOffhand + 1;
            slotMain = inventory.currentItem;
        } else {
            if (switchableBack) {
                slotOffhandBack = slotOffhand;
            }
            slotOffhand = NOT_SELECTED;
        }
    }

    /** Closes both hotbars, e.g. when a GUI screen opens or VR stops. */
    public void closeAll() {
        mainRadial.close();
        offhandRadial.close();
        pressedMain = false;
        pressedOffhand = false;
        slotMainBack = NULL;
        slotOffhandBack = NULL;
        resetData = true;
    }

    // ------------------------------------------------------------------ queries

    public boolean isSelectorActive() {
        return mainRadial.active || offhandRadial.active;
    }

    public boolean isMainSelectorActive() {
        return mainRadial.active;
    }

    public boolean isOffhandSelectorActive() {
        return offhandRadial.active;
    }

    public boolean isMainHand() {
        return mainRadial.active;
    }

    public int getSlotMain() {
        return slotMain;
    }

    public int getOffhandSlot() {
        return slotOffhand;
    }

    /** Assigns the off hand slot; if it equals the main slot the main hand moves on (Visor TaskHotBar). */
    public void setOffhandSlot(int slot) {
        if (slot == slotOffhand) {
            return;
        }
        slotOffhand = slot;
        slotMainBack = NULL;
        slotOffhandBack = NULL;
        Minecraft mc = Minecraft.getMinecraft();
        if (slotMain == slotOffhand && mc != null && mc.thePlayer != null && mc.thePlayer.inventory != null) {
            handleSlotCollision(mc.thePlayer.inventory, true, false);
        }
    }

    public ItemStack getOffhandItemStack(Minecraft mc) {
        if (slotOffhand >= 0 && slotOffhand < 9) {
            if (mc != null && mc.thePlayer != null && mc.thePlayer.inventory != null) {
                return mc.thePlayer.inventory.mainInventory[slotOffhand];
            }
        }
        if (mc != null && mc.thePlayer != null) {
            ItemStack bhItem = com.gtnhvr.util.BackhandCompat.getOffhandItem(mc.thePlayer);
            if (bhItem != null) return bhItem;
        }
        return null;
    }

    // ------------------------------------------------------------------ rendering

    /** Draws the hotbar textures once per frame, before the eye passes. */
    public void prepareTextures(Minecraft mc) {
        VRWristInventory.getInstance().prepareTextures(mc);
        VRWristStatusOverlay.getInstance().prepareTextures(mc);
        VRWristMapOverlay.getInstance().prepareTextures(mc);
        VRWristInfoOverlay.getInstance().prepareTextures(mc);
        VRWristPotionOverlay.getInstance().prepareTextures(mc);
        if (mainRadial.active) {
            mainRadial.renderer.render(mc, mainRadial.selectedSlice, slotMain, slotOffhand);
        }
        if (offhandRadial.active) {
            offhandRadial.renderer.render(mc, offhandRadial.selectedSlice, slotMain, slotOffhand);
        }
    }

    /** Draws the open hotbars as floating quads inside an eye pass (room space). */
    public void render(int eyeIndex, Vector3f hmdPos) {
        if (mainRadial.active) {
            renderQuad(mainRadial);
        }
        if (offhandRadial.active) {
            renderQuad(offhandRadial);
        }
    }

    void renderQuad(Radial radial) {
        int textureId = radial.renderer.getTextureId();
        if (textureId <= 0) {
            return;
        }
        float half = QUAD_WIDTH * 0.5F * radial.scale;

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glPushMatrix();
        try {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glDisable(GL11.GL_FOG);
            GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
            GLStateManager.glDepthMask(false);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // Compute quad corners directly in room space from center, right and up vectors.
            // This avoids glMultMatrixf which is unmapped in modern core GL profiles and crashes the JVM.
            Vector3f c = radial.center;
            float rx = radial.right.x * half;
            float ry = radial.right.y * half;
            float rz = radial.right.z * half;
            float ux = radial.up.x * half;
            float uy = radial.up.y * half;
            float uz = radial.up.z * half;

            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // top of the canvas is stored at v = 1
            GLStateManager.glBegin(GL11.GL_QUADS);
            GLStateManager.glTexCoord2f(0.0F, 0.0F);
            GLStateManager.glVertex3f(c.x - rx - ux, c.y - ry - uy, c.z - rz - uz);
            GLStateManager.glTexCoord2f(1.0F, 0.0F);
            GLStateManager.glVertex3f(c.x + rx - ux, c.y + ry - uy, c.z + rz - uz);
            GLStateManager.glTexCoord2f(1.0F, 1.0F);
            GLStateManager.glVertex3f(c.x + rx + ux, c.y + ry + uy, c.z + rz + uz);
            GLStateManager.glTexCoord2f(0.0F, 1.0F);
            GLStateManager.glVertex3f(c.x - rx + ux, c.y - ry + uy, c.z - rz + uz);
            GLStateManager.glEnd();
        } finally {
            GLStateManager.glPopMatrix();
            GLStateManager.glPopAttrib();
        }
    }

    public void destroy() {
        mainRadial.renderer.destroy();
        offhandRadial.renderer.destroy();
    }
}
