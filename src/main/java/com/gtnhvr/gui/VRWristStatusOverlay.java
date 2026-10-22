package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VROverlayConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Wrist Status HUD Overlay (Health, Hunger, Armor, Air, Experience) attached to the offhand (left wrist).
 * Captures the exact, authentic in-game HUD (including GTNH/TConstruct colored hearts, AppleCore saturation,
 * and modded armor bars) from the HUD render target, rendering it as a crisp, stylish smartwatch display.
 * Fully customizable via GuiSettingsOverlays (VROverlayConfig.STATUS).
 */
@Lwjgl3Aware
public final class VRWristStatusOverlay {
    private static final VRWristStatusOverlay INSTANCE = new VRWristStatusOverlay();

    private boolean active = true;
    private final Vector3f center = new Vector3f();
    private final Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);
    private final Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
    private final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);

    private VRWristStatusOverlay() {}

    public static VRWristStatusOverlay getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.STATUS);
        if (!cfg.visible || mc == null || mc.thePlayer == null || mc.currentScreen != null) {
            active = false;
            return;
        }

        // Hide status when full inventory is open to prevent overlapping
        if (VRWristInventory.getInstance().isActive()) {
            active = false;
            return;
        }

        XrPosef head = OpenXRRuntime.getHeadPose();
        if (head == null) {
            active = false;
            return;
        }

        boolean wristIsLeft = !"MAIN_HAND".equals(cfg.anchor);
        boolean tracked = wristIsLeft ? handler.isLeftHandTracked() : handler.isRightHandTracked();
        if (!tracked) {
            active = false;
            return;
        }

        Vector3f wristPos = wristIsLeft ? handler.getLeftHandPos() : handler.getRightHandPos();
        Quaternionf wristRot = wristIsLeft ? handler.getLeftHandRot() : handler.getRightHandRot();

        // Follow Visor defaults_hand_hud.yml (hud_health):
        // OFFHAND: offset (0.06, 0.0, 0.124), rotY = +90 deg
        // MAIN_HAND: offset (-0.06, 0.0, 0.124), rotY = -90 deg
        Vector3f posOffset = new Vector3f(
                wristIsLeft ? 0.06F : -0.06F,
                0.0F,
                0.124F
        );
        Quaternionf rotOffset = new Quaternionf().rotationY(
                wristIsLeft ? (float) (Math.PI / 2.0) : -(float) (Math.PI / 2.0)
        );
        Quaternionf panelRot = new Quaternionf(wristRot).mul(rotOffset);

        panelRot.transform(new Vector3f(1.0F, 0.0F, 0.0F), right);
        panelRot.transform(new Vector3f(0.0F, 1.0F, 0.0F), up);
        panelRot.transform(new Vector3f(0.0F, 0.0F, 1.0F), normal);

        center.set(wristPos).add(wristRot.transform(posOffset, new Vector3f()))
                .add(new Vector3f(right).mul(cfg.offsetX))
                .add(new Vector3f(up).mul(cfg.offsetY));

        Vector3f hmdPos = new Vector3f(head.position$().x(), head.position$().y(), head.position$().z());
        Quaternionf hmdRot = new Quaternionf(head.orientation().x(), head.orientation().y(),
                head.orientation().z(), head.orientation().w());
        Vector3f hmdForward = hmdRot.transform(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());

        Vector3f toHead = new Vector3f(hmdPos).sub(center);
        float distance = toHead.length();
        if (distance < 0.05F || distance > (active ? 1.10F : 0.90F)) {
            active = false;
            return;
        }
        toHead.div(distance);

        float facingDot = normal.dot(toHead);
        boolean facing = facingDot > (active ? 0.10F : 0.25F);

        Vector3f toHand = new Vector3f(center).sub(hmdPos).normalize();
        float glanceDot = hmdForward.dot(toHand);
        boolean glancing = glanceDot > (active ? 0.10F : 0.20F);

        active = facing && glancing;
    }

    public void prepareTextures(Minecraft mc) {
        // Direct capture from HUD texture in render(), no separate blit pass needed
    }

    public void render() {
        if (!active) return;
        int hudTex = VRHudRenderer.getTextureId();
        if (hudTex <= 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        ScaledResolution scaled = new ScaledResolution(mc, VRHudRenderer.getWidth(), VRHudRenderer.getHeight());
        int sw = scaled.getScaledWidth();
        int sh = scaled.getScaledHeight();
        if (sw <= 0 || sh <= 0) return;

        int cx = sw / 2;

        // Bounding box of the entire Minecraft / GTNH bottom HUD:
        // Left: cx - 93, Right: cx + 93 (186 scaled units)
        // Covers Armor / Air, TConstruct colored health hearts, hunger/saturation, XP bar and level
        int minX = cx - 93;
        int maxX = cx + 93;
        int minY = sh - 53;
        int maxY = sh - 20;

        float u0 = (float) minX / (float) sw;
        float u1 = (float) maxX / (float) sw;
        float v0 = 1.0F - (float) maxY / (float) sh;
        float v1 = 1.0F - (float) minY / (float) sh;

        float aspect = (float) (maxX - minX) / (float) Math.max(1, maxY - minY); // ~ 5.63

        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.STATUS);
        float widthMeters = 0.22F * cfg.scale;
        float heightMeters = widthMeters / aspect;
        float halfWidth = widthMeters * 0.5F;
        float halfHeight = heightMeters * 0.5F;

        Vector3f r = new Vector3f(right).mul(halfWidth);
        Vector3f u = new Vector3f(up).mul(halfHeight);

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glDisable(GL11.GL_FOG);
            GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
            GLStateManager.glDepthMask(false);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // Modded In-Game Status HUD (Colored hearts, armor, saturation food, XP bar) - 100% transparent, NO BLUE BACKGROUND
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, hudTex);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            GLStateManager.glBegin(GL11.GL_QUADS);
            GLStateManager.glTexCoord2f(u0, v0);
            GLStateManager.glVertex3f(center.x - r.x - u.x, center.y - r.y - u.y, center.z - r.z - u.z);
            GLStateManager.glTexCoord2f(u1, v0);
            GLStateManager.glVertex3f(center.x + r.x - u.x, center.y + r.y - u.y, center.z + r.z - u.z);
            GLStateManager.glTexCoord2f(u1, v1);
            GLStateManager.glVertex3f(center.x + r.x + u.x, center.y + r.y + u.y, center.z + r.z + u.z);
            GLStateManager.glTexCoord2f(u0, v1);
            GLStateManager.glVertex3f(center.x - r.x + u.x, center.y - r.y + u.y, center.z - r.z + u.z);
            GLStateManager.glEnd();
        } finally {
            GLStateManager.glPopAttrib();
        }
    }
}
