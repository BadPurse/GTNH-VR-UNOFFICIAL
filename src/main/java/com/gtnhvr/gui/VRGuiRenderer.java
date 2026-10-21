package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.OpenXRRuntime;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * Dedicated GUI capture framebuffer for Minecraft 1.7.10.
 * In 1.7.10, GuiScreens and menus are rendered directly into FBO 0 or mc.getFramebuffer().
 * This class captures all 2D GUI rendering into an offscreen texture
 * so VRVirtualScreen can present it as a floating 3D screen in VR.
 *
 * It seamlessly redirects Minecraft.framebufferMc to VRGuiFramebuffer during capture,
 * preventing mods like NEI and BetterQuesting from leaking elements onto the desktop mirror
 * or causing VR flickering by unbinding the capture target.
 */
@Lwjgl3Aware
public final class VRGuiRenderer {
    private static int fbo = 0;
    private static int texture = 0;
    private static int depthRb = 0;
    private static int width = 0;
    private static int height = 0;
    private static boolean capturing = false;
    private static int previousFbo = 0;
    private static long frameId = 0L;
    private static long clearedFrameId = -1L;
    private static boolean clearedAsMenu = false;

    private static VRGuiFramebuffer guiFramebuffer = null;
    private static Framebuffer savedMinecraftFramebuffer = null;
    private static int savedDisplayWidth = -1;
    private static int savedDisplayHeight = -1;

    public static int getCaptureWidth() {
        if (com.gtnhvr.settings.VRClientSettings.autonomousResolution) {
            return Math.max(320, com.gtnhvr.settings.VRClientSettings.virtualScreenWidth);
        }
        Minecraft mc = Minecraft.getMinecraft();
        return (mc != null && mc.displayWidth > 0) ? mc.displayWidth : 1920;
    }

    public static int getCaptureHeight() {
        if (com.gtnhvr.settings.VRClientSettings.autonomousResolution) {
            return Math.max(240, com.gtnhvr.settings.VRClientSettings.virtualScreenHeight);
        }
        Minecraft mc = Minecraft.getMinecraft();
        return (mc != null && mc.displayHeight > 0) ? mc.displayHeight : 1080;
    }

    private static void useLogicalGuiResolution(Minecraft mc) {
        if (mc == null) return;
        if (savedDisplayWidth < 0 || savedDisplayHeight < 0) {
            savedDisplayWidth = mc.displayWidth;
            savedDisplayHeight = mc.displayHeight;
        }
        mc.displayWidth = width;
        mc.displayHeight = height;
        if (mc.currentScreen != null) {
            net.minecraft.client.gui.ScaledResolution scaled =
                    new net.minecraft.client.gui.ScaledResolution(mc, width, height);
            if (mc.currentScreen.width != scaled.getScaledWidth()
                    || mc.currentScreen.height != scaled.getScaledHeight()) {
                mc.currentScreen.setWorldAndResolution(mc, scaled.getScaledWidth(), scaled.getScaledHeight());
            }
        }
    }

    private static void restoreWindowResolution(Minecraft mc) {
        if (mc != null && savedDisplayWidth >= 0 && savedDisplayHeight >= 0) {
            mc.displayWidth = savedDisplayWidth;
            mc.displayHeight = savedDisplayHeight;
        }
        savedDisplayWidth = -1;
        savedDisplayHeight = -1;
    }

    /** Called once at the start of every render tick; allows exactly one clear per frame. */
    public static void newFrame() {
        frameId++;
    }

    private VRGuiRenderer() {}

    public static void checkSize(int w, int h) {
        if (w <= 0) w = 854;
        if (h <= 0) h = 480;
        if (fbo == 0 || width != w || height != h) {
            destroy();
            width = w;
            height = h;

            fbo = GLStateManager.glGenFramebuffers();
            texture = GLStateManager.glGenTextures();
            depthRb = GLStateManager.glGenRenderbuffers();

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRb);
            GLStateManager.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            GLStateManager.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRb);
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }

        if (guiFramebuffer == null) {
            guiFramebuffer = new VRGuiFramebuffer(width, height);
        }
        guiFramebuffer.update(fbo, texture, depthRb, width, height);
    }

    public static void begin(int w, int h, boolean inMainMenu) {
        checkSize(w, h);
        Minecraft mc = Minecraft.getMinecraft();
        if (!capturing) {
            previousFbo = GLStateManager.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            if (mc != null) {
                savedMinecraftFramebuffer = OpenXRRuntime.getMinecraftFramebuffer(mc);
                OpenXRRuntime.setMinecraftFramebuffer(mc, guiFramebuffer);
                useLogicalGuiResolution(mc);
            }
        }
        capturing = true;
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glViewport(0, 0, width, height);

        // A second begin() in the same frame must not wipe what was already drawn
        // (NEI panels, container slots); the buffer is cleared once per frame only.
        if (clearedFrameId != frameId || clearedAsMenu != inMainMenu) {
            boolean scissor = GLStateManager.glIsEnabled(GL11.GL_SCISSOR_TEST);
            if (scissor) {
                GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
            }
            // Ensure depth mask and color mask allow full clearing
            GLStateManager.glDepthMask(true);
            GLStateManager.glColorMask(true, true, true, true);

            if (inMainMenu) {
                GLStateManager.glClearColor(0.06F, 0.07F, 0.10F, 1.0F);
            } else {
                GLStateManager.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            }
            GLStateManager.glClearDepth(1.0D);
            GLStateManager.glClearStencil(0);
            GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
            if (scissor) {
                GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
            }
            clearedFrameId = frameId;
            clearedAsMenu = inMainMenu;
        }

        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.glEnable(GL11.GL_BLEND);
        com.gtnewhorizons.angelica.glsm.GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void end() {
        if (!capturing) return;
        capturing = false;

        // Ensure all color/alpha channels are writable
        GLStateManager.glColorMask(true, true, true, true);

        // Flush draw commands so GUI texture is completely ready for sampling
        GLStateManager.glFlush();

        // Restore vanilla Minecraft framebuffer
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && savedMinecraftFramebuffer != null) {
            OpenXRRuntime.setMinecraftFramebuffer(mc, savedMinecraftFramebuffer);
            savedMinecraftFramebuffer = null;
        }
        restoreWindowResolution(mc);

        // Restore previous framebuffer binding
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
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
        if (fbo != 0) {
            GLStateManager.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (texture != 0) {
            GLStateManager.glDeleteTextures(texture);
            texture = 0;
        }
        if (depthRb != 0) {
            GLStateManager.glDeleteRenderbuffers(depthRb);
            depthRb = 0;
        }
        if (guiFramebuffer != null) {
            guiFramebuffer = null;
        }
        width = 0;
        height = 0;
        capturing = false;
        previousFbo = 0;
        Minecraft mc = Minecraft.getMinecraft();
        restoreWindowResolution(mc);
        savedMinecraftFramebuffer = null;
    }
}
