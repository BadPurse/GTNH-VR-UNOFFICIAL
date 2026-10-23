package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.OpenXRRuntime;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Owns the OpenGL/framebuffer scope for every 2D VR GUI pass.
 *
 * <p>The important invariant is that Minecraft.displayWidth/displayHeight always remain the
 * physical window dimensions. GUI sizing is virtualized at ScaledResolution construction time;
 * render passes only bind a target, install its projection and restore the previous GL state.</p>
 *
 * <p>This mirrors Visor's separation between overlay render targets and the overlay manager:
 * neither a screen renderer nor a HUD renderer may independently mutate global window state.</p>
 */
@Lwjgl3Aware
public final class VRGuiRenderPipeline {
    public enum Pass {
        SCREEN,
        HUD
    }

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

    /**
     * Starts a scoped GUI pass. Returns false rather than taking ownership of an already-active
     * pass, so a nested Forge/mod callback cannot silently replace the target of its caller.
     */
    public static boolean begin(Pass pass, int framebufferId, int width, int height,
                                boolean opaqueMenuBackground, Framebuffer target) {
        if (pass == null || activePass != null || width <= 0 || height <= 0) {
            return false;
        }

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

            ScaledResolution scaled = mc == null
                    ? null : new ScaledResolution(mc, width, height);
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
        if (activePass == null || activePass != pass) {
            return;
        }

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
                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebufferId);
                VRGuiRenderer.setLogicalGuiResolutionActive(previousLogicalResolutionActive);
                previousMinecraftFramebuffer = null;
                hadMinecraftInstance = false;
                previousFramebufferId = 0;
                activePass = null;
            }
        }
    }

    public static boolean isActive() {
        return activePass != null;
    }

    public static Pass getActivePass() {
        return activePass;
    }
}
