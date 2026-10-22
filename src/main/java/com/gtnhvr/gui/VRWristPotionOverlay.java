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
 * Wrist overlay for Bottom-Right HUD (InGameInfoXML Active Potion Effects):
 * Captures active potion effects, icons, and remaining duration timers
 * from the bottom-right region of the in-game HUD and projects it cleanly onto the left forearm
 * directly above the Health & Hunger status bar.
 * Automatically hides when no potion effects are active or when wrist inventory is open.
 * NO BLUE BACKGROUND - 100% clean transparent AR presentation.
 */
@Lwjgl3Aware
public final class VRWristPotionOverlay {
    private static final VRWristPotionOverlay INSTANCE = new VRWristPotionOverlay();

    private final Vector3f center = new Vector3f();
    private final Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
    private final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);

    private boolean active = false;

    private VRWristPotionOverlay() {}

    public static VRWristPotionOverlay getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.POTIONS);
        if (!cfg.visible || mc == null || mc.thePlayer == null || mc.currentScreen != null) {
            active = false;
            return;
        }

        // Hide when full wrist inventory is open
        if (VRWristInventory.getInstance().isActive()) {
            active = false;
            return;
        }

        // Hide if player has no active potion effects
        if (mc.thePlayer.getActivePotionEffects().isEmpty()) {
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

        // Placed along forearm, naturally above health/hunger bar
        Vector3f posOffset = new Vector3f(
                wristIsLeft ? 0.06F : -0.06F,
                0.0F,
                0.12F
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
        // Direct sample from HUD texture in render()
    }

    public void render() {
        if (!active) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.thePlayer.getActivePotionEffects().isEmpty()) return;

        int hudTex = VRHudRenderer.getTextureId();
        if (hudTex <= 0) return;

        ScaledResolution scaled = new ScaledResolution(mc, VRHudRenderer.getWidth(), VRHudRenderer.getHeight());
        int sw = scaled.getScaledWidth();
        int sh = scaled.getScaledHeight();
        if (sw <= 0 || sh <= 0) return;

        // Bounding box for Bottom-Right HUD (InGameInfoXML Potion effects):
        // Captures from x: sw - 140 to sw, y: sh - 110 to sh
        int minX = Math.max(0, sw - 140);
        int maxX = sw;
        int minY = Math.max(0, sh - 110);
        int maxY = sh;

        float u0 = (float) minX / (float) sw;
        float u1 = (float) maxX / (float) sw;
        float v0 = 1.0F - (float) maxY / (float) sh;
        float v1 = 1.0F - (float) minY / (float) sh;

        float aspect = (float) (maxX - minX) / (float) Math.max(1, maxY - minY);

        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.POTIONS);
        float widthMeters = 0.12F * cfg.scale;
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

            // Clean 100% transparent background (NO BLUE, NO BORDER)
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, hudTex);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, cfg.opacity);

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
