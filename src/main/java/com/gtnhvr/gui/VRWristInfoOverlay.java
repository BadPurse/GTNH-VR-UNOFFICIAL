package com.gtnhvr.gui;

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
 * Wrist overlay for Top-Left HUD (InGameInfoXML / WAILA):
 * Captures coordinates, biome, time, day, light level, FPS, dimension, and entity counts
 * from the top-left region of the in-game HUD and projects it cleanly onto the right forearm
 * directly above the JourneyMap minimap.
 * NO BLUE BACKGROUND - 100% clean transparent AR presentation.
 */
@Lwjgl3Aware
public final class VRWristInfoOverlay {
    private static final VRWristInfoOverlay INSTANCE = new VRWristInfoOverlay();

    private final Vector3f center = new Vector3f();
    private final Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
    private final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);

    private boolean active = false;

    private VRWristInfoOverlay() {}

    public static VRWristInfoOverlay getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.INFO);
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

        // Placed along forearm, naturally above minimap
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
        // Direct sample from HUD texture in render()
    }

    public void render() {
        if (!active) return;
        int hudTex = VRHudRenderer.getTextureId();
        if (hudTex <= 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        // Use autonomous resolution dimensions for HUD region calculation
        int dispW = com.gtnhvr.gui.VRGuiRenderer.getCaptureWidth();
        int dispH = com.gtnhvr.gui.VRGuiRenderer.getCaptureHeight();
        ScaledResolution scaled = new ScaledResolution(mc, dispW, dispH);
        int sw = scaled.getScaledWidth();
        int sh = scaled.getScaledHeight();
        if (sw <= 0 || sh <= 0) return;

        // Bounding box for Top-Left HUD (InGameInfoXML + WAILA):
        // Captures from x: 0 to 260, y: 0 to 145
        int minX = 0;
        int maxX = Math.min(sw, 260);
        int minY = 0;
        int maxY = Math.min(sh, 145);

        float u0 = (float) minX / (float) sw;
        float u1 = (float) maxX / (float) sw;
        float v0 = 1.0F - (float) maxY / (float) sh;
        float v1 = 1.0F - (float) minY / (float) sh;

        float aspect = (float) (maxX - minX) / (float) Math.max(1, maxY - minY);

        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.INFO);
        float widthMeters = 0.18F * cfg.scale;
        float heightMeters = widthMeters / aspect;
        float halfWidth = widthMeters * 0.5F;
        float halfHeight = heightMeters * 0.5F;

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

            // Clean 100% transparent background (NO BLUE, NO BORDER)
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hudTex);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(u0, v0);
            GL11.glVertex3f(center.x - r.x - u.x, center.y - r.y - u.y, center.z - r.z - u.z);
            GL11.glTexCoord2f(u1, v0);
            GL11.glVertex3f(center.x + r.x - u.x, center.y + r.y - u.y, center.z + r.z - u.z);
            GL11.glTexCoord2f(u1, v1);
            GL11.glVertex3f(center.x + r.x + u.x, center.y + r.y + u.y, center.z + r.z + u.z);
            GL11.glTexCoord2f(u0, v1);
            GL11.glVertex3f(center.x - r.x + u.x, center.y - r.y + u.y, center.z - r.z + u.z);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }
}
