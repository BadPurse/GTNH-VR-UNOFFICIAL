package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.OpenXRRuntime;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * Single owner of the VR 2D render-target registry and its OpenGL scopes.
 *
 * <p>Screen GUIs and the in-game HUD have separate texture targets, but share the same lifecycle:
 * allocate/resize, bind, render on a logical ScaledResolution canvas, then restore the exact
 * previous target and GL state. The physical Minecraft window dimensions are never changed.</p>
 *
 * <p>This is the Minecraft 1.7.10 equivalent of Visor's GUI target manager and overlay render
 * pass. The screen/HUD facades expose textures and pose rendering; they do not own GL resources.</p>
 */
@Lwjgl3Aware
public final class VRGuiRenderPipeline {
    public enum Pass {
        SCREEN,
        HUD
    }

    private static final Target SCREEN_TARGET = new Target();
    private static final Target HUD_TARGET = new Target();

    private static Pass activePass;
    private static int previousFramebufferId;
    private static int previousMatrixMode = GL11.GL_MODELVIEW;
    private static Framebuffer previousMinecraftFramebuffer;
    private static boolean hadMinecraftInstance;
    private static boolean previousLogicalResolutionActive;
    private static boolean attribPushed;
    private static boolean projectionPushed;
    private static boolean modelViewPushed;

    private VRGuiRenderPipeline() {}

    private static Target getTarget(Pass pass) {
        if (pass == Pass.SCREEN) return SCREEN_TARGET;
        if (pass == Pass.HUD) return HUD_TARGET;
        throw new IllegalArgumentException("Unknown VR GUI render pass: " + pass);
    }

    /** Allocates or resizes a target once, outside the target's drawing body. */
    public static void ensureTarget(Pass pass, int width, int height) {
        if (width <= 0) width = 854;
        if (height <= 0) height = 480;
        Target target = getTarget(pass);
        if (target.isSize(width, height)) return;
        if (activePass == pass) end(pass);
        target.ensureSize(width, height);
    }

    public static int getFramebufferId(Pass pass) {
        return getTarget(pass).framebufferId;
    }

    public static int getTextureId(Pass pass) {
        return getTarget(pass).textureId;
    }

    public static int getDepthBufferId(Pass pass) {
        return getTarget(pass).depthBufferId;
    }

    public static int getWidth(Pass pass) {
        return getTarget(pass).width;
    }

    public static int getHeight(Pass pass) {
        return getTarget(pass).height;
    }

    public static VRGuiFramebuffer getTargetFramebuffer(Pass pass) {
        return getTarget(pass).framebuffer;
    }

    public static void destroyTarget(Pass pass) {
        if (activePass == pass) end(pass);
        getTarget(pass).destroy();
    }

    /**
     * Begins the selected render target. A nested mod callback cannot replace the target already
     * owned by its caller; callers must end their own pass in the matching lifecycle hook.
     */
    public static boolean begin(Pass pass, boolean opaqueMenuBackground) {
        if (pass == null || activePass != null) return false;
        Target target = getTarget(pass);
        if (target.framebufferId == 0 || target.framebuffer == null
                || target.width <= 0 || target.height <= 0) {
            return false;
        }
        return begin(pass, target.framebufferId, target.width, target.height,
                opaqueMenuBackground, target.framebuffer);
    }

    private static boolean begin(Pass pass, int framebufferId, int width, int height,
                                 boolean opaqueMenuBackground, Framebuffer target) {
        if (pass == null || activePass != null || width <= 0 || height <= 0) return false;

        Minecraft mc = Minecraft.getMinecraft();
        activePass = pass;
        previousFramebufferId = GLStateManager.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        previousMatrixMode = GLStateManager.glGetInteger(GL11.GL_MATRIX_MODE);
        hadMinecraftInstance = mc != null;
        previousMinecraftFramebuffer = mc == null ? null : OpenXRRuntime.getMinecraftFramebuffer(mc);
        previousLogicalResolutionActive = VRGuiRenderer.isLogicalGuiResolutionActive();

        try {
            GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attribPushed = true;

            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPushMatrix();
            projectionPushed = true;
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPushMatrix();
            modelViewPushed = true;

            if (mc != null && target != null) {
                OpenXRRuntime.setMinecraftFramebuffer(mc, target);
            }

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
            GLStateManager.glViewport(0, 0, width, height);
            GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
            GLStateManager.glDepthMask(true);
            GLStateManager.glColorMask(true, true, true, true);

            if (opaqueMenuBackground) {
                GLStateManager.glClearColor(0.06F, 0.07F, 0.10F, 1.0F);
            } else {
                GLStateManager.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            }
            GLStateManager.glClearDepth(1.0D);
            GLStateManager.glClearStencil(0);
            GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                    | GL11.GL_STENCIL_BUFFER_BIT);

            ScaledResolution scaled = mc == null ? null : new ScaledResolution(mc, width, height);
            double logicalWidth = scaled == null ? width : scaled.getScaledWidth_double();
            double logicalHeight = scaled == null ? height : scaled.getScaledHeight_double();

            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glLoadIdentity();
            GLStateManager.glOrtho(0.0D, logicalWidth, logicalHeight, 0.0D, 1000.0D, 3000.0D);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glLoadIdentity();
            GLStateManager.glTranslatef(0.0F, 0.0F, -2000.0F);

            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            VRGuiRenderer.setLogicalGuiResolutionActive(true);
            return true;
        } catch (RuntimeException | Error failure) {
            end(pass);
            throw failure;
        }
    }

    /** Restores the exact framebuffer, target pointer, matrices and GL attributes saved by begin. */
    public static void end(Pass pass) {
        if (activePass == null || activePass != pass) return;

        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (mc != null && hadMinecraftInstance) {
                OpenXRRuntime.setMinecraftFramebuffer(mc, previousMinecraftFramebuffer);
            }
        } finally {
            try {
                if (modelViewPushed) {
                    GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                    GLStateManager.glPopMatrix();
                    modelViewPushed = false;
                }
                if (projectionPushed) {
                    GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
                    GLStateManager.glPopMatrix();
                    projectionPushed = false;
                }
                if (attribPushed) {
                    GLStateManager.glMatrixMode(previousMatrixMode);
                    GLStateManager.glPopAttrib();
                    attribPushed = false;
                }
            } finally {
                try {
                    GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebufferId);
                } finally {
                    VRGuiRenderer.setLogicalGuiResolutionActive(previousLogicalResolutionActive);
                    previousMinecraftFramebuffer = null;
                    hadMinecraftInstance = false;
                    previousFramebufferId = 0;
                    activePass = null;
                }
            }
        }
    }

    public static boolean isActive() {
        return activePass != null;
    }

    public static Pass getActivePass() {
        return activePass;
    }

    /** One shared GL resource implementation used by the screen and HUD target facades. */
    private static final class Target {
        private int framebufferId;
        private int textureId;
        private int depthBufferId;
        private int width;
        private int height;
        private VRGuiFramebuffer framebuffer;

        boolean isSize(int w, int h) {
            return framebufferId != 0 && width == w && height == h;
        }

        void ensureSize(int w, int h) {
            if (isSize(w, h)) return;

            int oldFramebuffer = framebufferId;
            int oldTexture = textureId;
            int oldDepth = depthBufferId;
            int restoreFramebuffer = GLStateManager.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int restoreTexture = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int restoreRenderbuffer = GLStateManager.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);

            deleteGlResources();
            width = w;
            height = h;

            framebufferId = GLStateManager.glGenFramebuffers();
            textureId = GLStateManager.glGenTextures();
            depthBufferId = GLStateManager.glGenRenderbuffers();

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                    width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthBufferId);
            GLStateManager.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
            GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, textureId, 0);
            GLStateManager.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, depthBufferId);

            if (framebuffer == null) framebuffer = new VRGuiFramebuffer(width, height);
            framebuffer.update(framebufferId, textureId, depthBufferId, width, height);

            // Never leave a deleted target bound when resizing while another pass is active.
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER,
                    restoreFramebuffer == oldFramebuffer ? 0 : restoreFramebuffer);
            GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER,
                    restoreRenderbuffer == oldDepth ? 0 : restoreRenderbuffer);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D,
                    restoreTexture == oldTexture ? 0 : restoreTexture);
        }

        void destroy() {
            int oldFramebuffer = framebufferId;
            int oldTexture = textureId;
            int oldDepth = depthBufferId;
            int restoreFramebuffer = GLStateManager.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int restoreTexture = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int restoreRenderbuffer = GLStateManager.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);

            deleteGlResources();
            width = 0;
            height = 0;
            framebuffer = null;

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER,
                    restoreFramebuffer == oldFramebuffer ? 0 : restoreFramebuffer);
            GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER,
                    restoreRenderbuffer == oldDepth ? 0 : restoreRenderbuffer);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D,
                    restoreTexture == oldTexture ? 0 : restoreTexture);
        }

        private void deleteGlResources() {
            if (framebufferId != 0) {
                GLStateManager.glDeleteFramebuffers(framebufferId);
                framebufferId = 0;
            }
            if (textureId != 0) {
                GLStateManager.glDeleteTextures(textureId);
                textureId = 0;
            }
            if (depthBufferId != 0) {
                GLStateManager.glDeleteRenderbuffers(depthBufferId);
                depthBufferId = 0;
            }
        }
    }
}
