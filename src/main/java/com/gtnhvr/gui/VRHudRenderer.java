package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.settings.VROverlayConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * Dedicated HUD capture framebuffer for in-game VR vital stats:
 * Captures health, hunger, armor, air, and minimap into an offscreen texture,
 * while hiding the flat 2D bottom hotbar (which is replaced by the 3x3 radial hotbar).
 * Renders the vital HUD as a comfortable floating display in VR space.
 */
@Lwjgl3Aware
public final class VRHudRenderer {
    private static int fbo = 0;
    private static int texture = 0;
    private static int depthRb = 0;
    private static int width = 0;
    private static int height = 0;
    private static VRGuiFramebuffer hudFramebuffer = null;
    private static boolean capturing = false;

    private VRHudRenderer() {}

    public static void checkSize(int w, int h) {
        if (w <= 0) w = 854;
        if (h <= 0) h = 480;
        if (capturing && (width != w || height != h)) end();

        VRGuiRenderPipeline.ensureTarget(VRGuiRenderPipeline.Pass.HUD, w, h);
        fbo = VRGuiRenderPipeline.getFramebufferId(VRGuiRenderPipeline.Pass.HUD);
        texture = VRGuiRenderPipeline.getTextureId(VRGuiRenderPipeline.Pass.HUD);
        depthRb = VRGuiRenderPipeline.getDepthBufferId(VRGuiRenderPipeline.Pass.HUD);
        width = VRGuiRenderPipeline.getWidth(VRGuiRenderPipeline.Pass.HUD);
        height = VRGuiRenderPipeline.getHeight(VRGuiRenderPipeline.Pass.HUD);
        hudFramebuffer = VRGuiRenderPipeline.getTargetFramebuffer(VRGuiRenderPipeline.Pass.HUD);
    }

    /** Capture the HUD into its target through the same scoped pipeline as screen GUIs. */
    public static void begin(int w, int h) {
        if (capturing || !OpenXRRuntime.isSessionRunning()) return;
        checkSize(w, h);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || hudFramebuffer == null) return;

        boolean began = VRGuiRenderPipeline.begin(VRGuiRenderPipeline.Pass.HUD, false);
        if (!began) return;
        capturing = true;
    }

    public static void end() {
        if (!capturing) return;
        try {
            VRGuiRenderPipeline.end(VRGuiRenderPipeline.Pass.HUD);
        } finally {
            capturing = false;
        }
    }

    private static final Vector3f currentHudPos = new Vector3f();
    private static final Quaternionf currentHudRot = new Quaternionf();
    private static long lastUpdateNanos = 0L;
    private static boolean hudInitialized = false;
    private static String lastAnchor = "HMD";

    public static void resetSmoothing() {
        hudInitialized = false;
    }


    /**
     * Renders floating 3D HUD quad in VR with authentic Virtual Desktop Head Lock.
     * Continuously floats with gentle, silky-smooth inertia towards head gaze,
     * using direct matrix multiplication to eliminate all quaternion flipping or jerkiness.
     */
    public static void render(int eyeIndex, Vector3f hmdPos, Quaternionf hmdRot) {
        if (texture <= 0 || width <= 0 || height <= 0) return;

        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.HUD);
        // User-created HUD copies render independently of the visibility of the built-in HUD.
        VRCustomOverlayRenderer.renderFromPose(VROverlayConfig.HUD, eyeIndex, hmdPos, hmdRot,
                texture, width, height, 1.35F, 1.0F, 1.0F, false);
        if (!cfg.visible) return;

        if (eyeIndex == 0) {
            long now = System.nanoTime();
            if (!cfg.anchor.equals(lastAnchor)) {
                lastAnchor = cfg.anchor;
                hudInitialized = false;
            }
            float dist = (VRClientSettings.hudDistance > 0.1F) ? VRClientSettings.hudDistance : cfg.distance;
            // 1:1 Visor VROverlayHUD PoseAnchor.HMD: offset (0, -0.10, -distance) in head space
            Vector3f headOffset = new Vector3f(cfg.offsetX, -0.10F + cfg.offsetY, -dist);

            if (cfg.anchor.equals("MAIN_HAND") || cfg.anchor.equals("OFFHAND")) {
                // Attached to a controller, always turned towards the head
                VRControllerHandler handler = VRControllerHandler.getInstance();
                boolean main = cfg.anchor.equals("MAIN_HAND");
                Vector3f handPos = main ? handler.getRightHandPos() : handler.getLeftHandPos();
                Quaternionf handRot = main ? handler.getRightHandRot() : handler.getLeftHandRot();
                currentHudPos.set(handPos).add(handRot.transform(new Vector3f(cfg.offsetX, cfg.offsetY, -dist), new Vector3f()));
                Vector3f toHead = new Vector3f(hmdPos).sub(currentHudPos);
                if (toHead.lengthSquared() > 1.0E-6F) {
                    currentHudRot.identity().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), toHead.normalize());
                } else {
                    currentHudRot.set(hmdRot);
                }
                lastUpdateNanos = now;
                hudInitialized = true;
            } else if (cfg.anchor.equals("WORLD")) {
                // Placed once in front of the head, then it stays in the room
                if (!hudInitialized) {
                    currentHudRot.set(hmdRot);
                    currentHudPos.set(hmdPos).add(currentHudRot.transform(headOffset, new Vector3f()));
                    lastUpdateNanos = now;
                    hudInitialized = true;
                }
            } else if (!hudInitialized) {
                currentHudRot.set(hmdRot);
                currentHudPos.set(hmdPos).add(currentHudRot.transform(headOffset, new Vector3f()));
                lastUpdateNanos = now;
                hudInitialized = true;
            } else {
                float dt = (now - lastUpdateNanos) * 1e-9F;
                lastUpdateNanos = now;
                dt = Math.max(0.001F, Math.min(0.05F, dt));

                // Silky-smooth follow towards HMD rotation and position (no Euler angles, no pitch inversion!)
                float rotFactor = 1.0F - (float) Math.exp(-6.0F * dt);
                currentHudRot.slerp(hmdRot, rotFactor);

                Vector3f targetPos = new Vector3f(hmdPos).add(currentHudRot.transform(headOffset, new Vector3f()));
                float posFactor = 1.0F - (float) Math.exp(-6.5F * dt);
                currentHudPos.lerp(targetPos, posFactor);
            }
        }

        Minecraft mc = Minecraft.getMinecraft();
        float aspect = (float) height / (float) width;
        float hudWidth = 1.35F * ((VRClientSettings.hudScale > 0.1F) ? VRClientSettings.hudScale : 1.0F); // meters wide
        float halfW = hudWidth * 0.5F;
        float halfH = hudWidth * aspect * 0.5F;

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glPushMatrix();

        try {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
            GLStateManager.glDepthMask(false);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.glEnable(GL11.GL_ALPHA_TEST);
            GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.08F);

            // Translate and apply smooth full 3D orientation
            GLStateManager.glTranslatef(currentHudPos.x, currentHudPos.y, currentHudPos.z);
            float qw = currentHudRot.w;
            float qx = currentHudRot.x;
            float qy = currentHudRot.y;
            float qz = currentHudRot.z;
            if (qw < 0.0F) {
                qw = -qw; qx = -qx; qy = -qy; qz = -qz;
            }
            float sinHalf = (float) Math.sqrt(Math.max(0.0F, 1.0F - qw * qw));
            if (sinHalf > 1e-4F) {
                float angleDeg = (float) Math.toDegrees(2.0 * Math.acos(Math.min(1.0F, qw)));
                GLStateManager.glRotatef(angleDeg, qx / sinHalf, qy / sinHalf, qz / sinHalf);
            }

            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, cfg.opacity);

            // Correct orientation: (0,0) is bottom-left, (1,1) is top-right (matching Visor RenderHelper & VRVirtualScreen)
            if (VRClientSettings.curvedHud) {
                // Cylinder around the viewer: the HUD centre sits `radius` metres ahead and the
                // edges bend back towards the head, keeping the arc length equal to hudWidth.
                float radius = (VRClientSettings.hudDistance > 0.1F) ? VRClientSettings.hudDistance : 1.20F;
                float arc = Math.min((float) Math.toRadians(150.0), hudWidth / radius);
                int segments = 32;
                GLStateManager.glBegin(GL11.GL_QUAD_STRIP);
                for (int i = 0; i <= segments; i++) {
                    float t = (float) i / (float) segments;
                    float a = (t - 0.5F) * arc;
                    float x = radius * (float) Math.sin(a);
                    float z = radius * (1.0F - (float) Math.cos(a));
                    GLStateManager.glTexCoord2f(t, 0.0F); GLStateManager.glVertex3f(x, -halfH, z);
                    GLStateManager.glTexCoord2f(t, 1.0F); GLStateManager.glVertex3f(x,  halfH, z);
                }
                GLStateManager.glEnd();
            } else {
                GLStateManager.glBegin(GL11.GL_QUADS);
                GLStateManager.glTexCoord2f(0.0F, 0.0F); GLStateManager.glVertex3f(-halfW, -halfH, 0.0F);
                GLStateManager.glTexCoord2f(1.0F, 0.0F); GLStateManager.glVertex3f( halfW, -halfH, 0.0F);
                GLStateManager.glTexCoord2f(1.0F, 1.0F); GLStateManager.glVertex3f( halfW,  halfH, 0.0F);
                GLStateManager.glTexCoord2f(0.0F, 1.0F); GLStateManager.glVertex3f(-halfW,  halfH, 0.0F);
                GLStateManager.glEnd();
            }

            GLStateManager.glDisable(GL11.GL_ALPHA_TEST);

        } finally {
            GLStateManager.glPopMatrix();
            GLStateManager.glPopAttrib();
        }
    }

    public static int getTextureId() {
        return texture;
    }

    public static int getWidth() {
        return width;
    }

    public static int getHeight() {
        return height;
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static void destroy() {
        if (capturing) end();
        VRGuiRenderPipeline.destroyTarget(VRGuiRenderPipeline.Pass.HUD);
        fbo = 0;
        texture = 0;
        depthRb = 0;
        hudFramebuffer = null;
        width = 0;
        height = 0;
        capturing = false;
    }
}
