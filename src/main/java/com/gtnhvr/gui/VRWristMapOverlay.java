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

import java.lang.reflect.Field;

/**
 * Wrist Map / Minimap Overlay attached to the main hand (right wrist).
 * Captures the authentic JourneyMap minimap directly from the HUD render target,
 * preserving its exact 1:1 aspect ratio, custom themes, waypoints, and compass orientation,
 * rendering it as a clean tactical smartwatch display on the player's right wrist.
 * Fully customizable via GuiSettingsOverlays (VROverlayConfig.MAP).
 */
@Lwjgl3Aware
public final class VRWristMapOverlay {
    private static final VRWristMapOverlay INSTANCE = new VRWristMapOverlay();

    private boolean active = true;
    private final Vector3f center = new Vector3f();
    private final Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);
    private final Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
    private final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);

    private VRWristMapOverlay() {}

    public static VRWristMapOverlay getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.MAP);
        if (!cfg.visible || mc == null || mc.thePlayer == null || mc.currentScreen != null) {
            active = false;
            return;
        }

        XrPosef head = OpenXRRuntime.getHeadPose();
        if (head == null) {
            active = false;
            return;
        }

        boolean wristIsRight = !"OFFHAND".equals(cfg.anchor);
        boolean tracked = wristIsRight ? handler.isRightHandTracked() : handler.isLeftHandTracked();
        if (!tracked) {
            active = false;
            return;
        }

        Vector3f wristPos = wristIsRight ? handler.getRightHandPos() : handler.getLeftHandPos();
        Quaternionf wristRot = wristIsRight ? handler.getRightHandRot() : handler.getLeftHandRot();

        // Follow Visor defaults_hand_hud.yml (hud_bottom_left / map):
        // MAIN_HAND (right): offset (-0.06F, 0.0F, 0.12F), rotY = -90 deg
        // OFFHAND (left): offset (0.06F, 0.0F, 0.12F), rotY = +90 deg
        Vector3f posOffset = new Vector3f(
                wristIsRight ? -0.06F : 0.06F,
                0.0F,
                0.12F
        );
        Quaternionf rotOffset = new Quaternionf().rotationY(
                wristIsRight ? -(float) (Math.PI / 2.0) : (float) (Math.PI / 2.0)
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

    private static float[] getJourneyMapUV(Minecraft mc) {
        ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sw = scaled.getScaledWidth();
        int sh = scaled.getScaledHeight();

        double minX = -1, maxX = -1, minY = -1, maxY = -1;
        try {
            Class<?> uiMgrCls = Class.forName("journeymap.client.ui.UIManager");
            Object uiMgr = uiMgrCls.getMethod("getInstance").invoke(null);
            Object miniMap = uiMgrCls.getMethod("getMiniMap").invoke(uiMgr);
            Field dvField = miniMap.getClass().getDeclaredField("dv");
            dvField.setAccessible(true);
            Object dv = dvField.get(miniMap);
            if (dv != null) {
                Field fCenterX = dv.getClass().getDeclaredField("centerX");
                Field fHalfW = dv.getClass().getDeclaredField("halfWidth");
                Field fTopY = dv.getClass().getDeclaredField("topY");
                Field fBottomY = dv.getClass().getDeclaredField("bottomY");
                fCenterX.setAccessible(true);
                fHalfW.setAccessible(true);
                fTopY.setAccessible(true);
                fBottomY.setAccessible(true);

                double cx = ((Number) fCenterX.get(dv)).doubleValue();
                double hw = ((Number) fHalfW.get(dv)).doubleValue();
                double ty = ((Number) fTopY.get(dv)).doubleValue();
                double by = ((Number) fBottomY.get(dv)).doubleValue();

                if (hw > 10 && by > ty) {
                    minX = cx - hw;
                    maxX = cx + hw;
                    minY = ty;
                    maxY = by;
                }
            }
        } catch (Throwable ignored) {}

        if (minX < 0) {
            // Standard fallback for JourneyMap TopRight (square 24% screen width)
            int mapSize = Math.max(80, (int) (sw * 0.24F));
            minX = sw - mapSize - 6;
            maxX = sw - 6;
            minY = 6;
            maxY = 6 + mapSize;
        }

        // Clamp to screen bounds
        minX = Math.max(0, Math.min(sw, minX));
        maxX = Math.max(0, Math.min(sw, maxX));
        minY = Math.max(0, Math.min(sh, minY));
        maxY = Math.max(0, Math.min(sh, maxY));

        float u0 = (float) minX / (float) sw;
        float u1 = (float) maxX / (float) sw;
        float v0 = 1.0F - (float) maxY / (float) sh;
        float v1 = 1.0F - (float) minY / (float) sh;

        float aspect = (float) (maxX - minX) / (float) Math.max(1.0, maxY - minY);
        return new float[]{u0, u1, v0, v1, aspect};
    }

    public void render() {
        if (!active) return;
        int hudTex = VRHudRenderer.getTextureId();
        if (hudTex <= 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        float[] uvAspect = getJourneyMapUV(mc);
        float u0 = uvAspect[0];
        float u1 = uvAspect[1];
        float v0 = uvAspect[2];
        float v1 = uvAspect[3];
        float aspect = uvAspect[4]; // width / height

        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.MAP);
        float widthMeters = 0.16F * cfg.scale;
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

            // Clean authentic JourneyMap minimap texture (100% transparent, NO BLUE BACKGROUND)
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
