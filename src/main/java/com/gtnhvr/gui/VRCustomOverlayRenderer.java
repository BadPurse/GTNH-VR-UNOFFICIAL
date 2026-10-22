package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.settings.VROverlayConfig;
import net.minecraft.client.Minecraft;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders user-created overlay copies using the texture of their selected template.
 *
 * GTNH's 1.7.10 port does not have Visor's generic addon/template registry, so this is
 * the compatibility layer for the templates that exist in this port: HUD, game screen,
 * and virtual keyboard. Configuration remains independent for each copy.
 */
public final class VRCustomOverlayRenderer {
    private static final class PoseState {
        final Vector3f position = new Vector3f();
        final Quaternionf rotation = new Quaternionf();
        boolean initialized;
    }

    private static final Map<String, PoseState> POSES = new HashMap<>();

    private VRCustomOverlayRenderer() {}

    public static void resetPoses() {
        POSES.clear();
    }

    public static void renderFromPose(String template, int eyeIndex,
            Vector3f hmdPosition, Quaternionf hmdRotation,
            int textureId, int textureWidth, int textureHeight,
            float baseWidth, float uMax, float vMax, boolean flipV) {
        if (hmdPosition == null || hmdRotation == null) return;
        render(template, eyeIndex, hmdPosition, hmdRotation, textureId,
                textureWidth, textureHeight, baseWidth, uMax, vMax, flipV);
    }

    public static void renderFromCurrentHead(String template, int eyeIndex,
            int textureId, int textureWidth, int textureHeight,
            float baseWidth, float uMax, float vMax, boolean flipV) {
        org.lwjgl.openxr.XrPosef headPose = OpenXRRuntime.getHeadPose();
        if (headPose == null) return;
        Vector3f position = new Vector3f(
                headPose.position$().x(),
                headPose.position$().y(),
                headPose.position$().z());
        Quaternionf rotation = new Quaternionf(
                headPose.orientation().x(),
                headPose.orientation().y(),
                headPose.orientation().z(),
                headPose.orientation().w());
        render(template, eyeIndex, position, rotation, textureId,
                textureWidth, textureHeight, baseWidth, uMax, vMax, flipV);
    }

    private static void render(String template, int eyeIndex,
            Vector3f hmdPosition, Quaternionf hmdRotation,
            int textureId, int textureWidth, int textureHeight,
            float baseWidth, float uMax, float vMax, boolean flipV) {
        if (textureId <= 0 || textureWidth <= 0 || textureHeight <= 0
                || baseWidth <= 0.0F) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        for (String id : VROverlayConfig.getCustomIds(template)) {
            VROverlayConfig.Entry config = VROverlayConfig.get(id);
            if (!config.visible) continue;

            PoseState pose = POSES.computeIfAbsent(id, key -> new PoseState());
            if (eyeIndex == 0 || !pose.initialized) {
                updatePose(pose, id, config, hmdPosition, hmdRotation);
            }
            if (!pose.initialized) continue;

            float width = baseWidth * Math.max(0.05F, Math.min(5.0F, config.scale));
            float aspect = (float) textureHeight / (float) textureWidth;
            float halfWidth = width * 0.5F;
            float halfHeight = width * aspect * 0.5F;

            GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GLStateManager.glPushMatrix();
            try {
                GLStateManager.glDisable(GL11.GL_CULL_FACE);
                GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
                GLStateManager.glDepthMask(false);
                GLStateManager.glEnable(GL11.GL_BLEND);
                GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
                GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                GLStateManager.glTranslatef(pose.position.x, pose.position.y, pose.position.z);
                AxisAngle4f axisAngle = new AxisAngle4f(pose.rotation);
                if (axisAngle.angle != 0.0F
                        && (axisAngle.x != 0.0F || axisAngle.y != 0.0F || axisAngle.z != 0.0F)) {
                    GLStateManager.glRotatef((float) Math.toDegrees(axisAngle.angle),
                            axisAngle.x, axisAngle.y, axisAngle.z);
                }

                if (VROverlayConfig.HUD.equals(config.template) && config.flag) {
                    float radius = Math.max(0.2F, config.distance);
                    float arc = Math.min((float) Math.toRadians(150.0), width / radius);
                    int segments = 32;
                    GLStateManager.glBegin(GL11.GL_QUAD_STRIP);
                    for (int i = 0; i <= segments; i++) {
                        float t = (float) i / (float) segments;
                        float angle = (t - 0.5F) * arc;
                        float x = radius * (float) Math.sin(angle);
                        float z = radius * (1.0F - (float) Math.cos(angle));
                        GLStateManager.glTexCoord2f(t, flipV ? 1.0F - vMax : 0.0F);
                        GLStateManager.glVertex3f(x, -halfHeight, z);
                        GLStateManager.glTexCoord2f(t, flipV ? 1.0F : vMax);
                        GLStateManager.glVertex3f(x, halfHeight, z);
                    }
                    GLStateManager.glEnd();
                } else {
                    GLStateManager.glBegin(GL11.GL_QUADS);
                    GLStateManager.glTexCoord2f(0.0F, flipV ? 1.0F - vMax : 0.0F);
                    GLStateManager.glVertex3f(-halfWidth, -halfHeight, 0.0F);
                    GLStateManager.glTexCoord2f(uMax, flipV ? 1.0F - vMax : 0.0F);
                    GLStateManager.glVertex3f(halfWidth, -halfHeight, 0.0F);
                    GLStateManager.glTexCoord2f(uMax, flipV ? 1.0F : vMax);
                    GLStateManager.glVertex3f(halfWidth, halfHeight, 0.0F);
                    GLStateManager.glTexCoord2f(0.0F, flipV ? 1.0F : vMax);
                    GLStateManager.glVertex3f(-halfWidth, halfHeight, 0.0F);
                    GLStateManager.glEnd();
                }
            } finally {
                GLStateManager.glPopMatrix();
                GLStateManager.glPopAttrib();
            }
        }
    }

    private static void updatePose(PoseState pose, String id, VROverlayConfig.Entry config,
            Vector3f hmdPosition, Quaternionf hmdRotation) {
        if ("WORLD".equals(config.anchor) && pose.initialized) return;

        if ("MAIN_HAND".equals(config.anchor) || "OFFHAND".equals(config.anchor)) {
            VRControllerHandler handler = VRControllerHandler.getInstance();
            boolean main = "MAIN_HAND".equals(config.anchor);
            Vector3f handPos = main ? handler.getRightHandPos() : handler.getLeftHandPos();
            Quaternionf handRot = main ? handler.getRightHandRot() : handler.getLeftHandRot();
            pose.position.set(handPos).add(handRot.transform(
                    new Vector3f(config.offsetX, config.offsetY, -config.distance),
                    new Vector3f()));
            pose.rotation.set(handRot);
        } else if ("WORLD".equals(config.anchor)) {
            pose.rotation.set(hmdRotation);
            pose.position.set(hmdPosition).add(hmdRotation.transform(
                    new Vector3f(config.offsetX, config.offsetY, -config.distance),
                    new Vector3f()));
        } else {
            pose.rotation.set(hmdRotation);
            pose.position.set(hmdPosition).add(hmdRotation.transform(
                    new Vector3f(config.offsetX, config.offsetY, -config.distance),
                    new Vector3f()));
        }
        pose.initialized = true;
    }
}
