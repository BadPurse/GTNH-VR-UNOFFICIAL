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
    private static boolean logicalGuiResolutionActive = false;
    private static boolean captureGlStatePushed = false;
    private static int previousMatrixMode = GL11.GL_MODELVIEW;
    private static int previousFbo = 0;
    private static long frameId = 0L;
    private static long clearedFrameId = -1L;
    private static boolean clearedAsMenu = false;

    private static VRGuiFramebuffer guiFramebuffer = null;
    private static Framebuffer savedMinecraftFramebuffer = null;
    private static int savedDisplayWidth = -1;
    private static int savedDisplayHeight = -1;
    private static int savedScaledGuiWidth = -1;
    private static int savedScaledGuiHeight = -1;

    // Same fixed GUI target as Visor's VRGuiManagerImpl. The screen canvas must not
    // inherit the desktop aspect ratio or the custom 2388x1668 default, which is not 16:9.
    private static final int VR_GUI_WIDTH = 1920;
    private static final int VR_GUI_HEIGHT = 1080;

    public static int getCaptureWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        if (com.gtnhvr.OpenXRRuntime.isSessionRunning()) return VR_GUI_WIDTH;
        return (mc != null && mc.displayWidth > 0) ? mc.displayWidth : VR_GUI_WIDTH;
    }

    public static int getCaptureHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        if (com.gtnhvr.OpenXRRuntime.isSessionRunning()) return VR_GUI_HEIGHT;
        return (mc != null && mc.displayHeight > 0) ? mc.displayHeight : VR_GUI_HEIGHT;
    }

    private static void useLogicalGuiResolution(Minecraft mc) {
        if (mc == null) return;
        // This flag is true only while a GUI/HUD render target is active, not during the 3D world pass.
        logicalGuiResolutionActive = true;
        if (savedDisplayWidth < 0 || savedDisplayHeight < 0) {
            savedDisplayWidth = mc.displayWidth;
            savedDisplayHeight = mc.displayHeight;
            net.minecraft.client.gui.ScaledResolution sourceScaled =
                    new net.minecraft.client.gui.ScaledResolution(mc, savedDisplayWidth, savedDisplayHeight);
            savedScaledGuiWidth = sourceScaled.getScaledWidth();
            savedScaledGuiHeight = sourceScaled.getScaledHeight();
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

    private static void setupLogicalGuiProjection(Minecraft mc) {
        if (mc == null) return;
        net.minecraft.client.gui.ScaledResolution scaled =
                new net.minecraft.client.gui.ScaledResolution(mc, width, height);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glOrtho(
                0.0D, scaled.getScaledWidth_double(),
                scaled.getScaledHeight_double(), 0.0D,
                1000.0D, 3000.0D);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(0.0F, 0.0F, -2000.0F);
    }

    private static void restoreWindowResolution(Minecraft mc) {
        if (mc != null && savedDisplayWidth >= 0 && savedDisplayHeight >= 0) {
            mc.displayWidth = savedDisplayWidth;
            mc.displayHeight = savedDisplayHeight;
        }
        savedDisplayWidth = -1;
        savedDisplayHeight = -1;
        savedScaledGuiWidth = -1;
        savedScaledGuiHeight = -1;
        logicalGuiResolutionActive = false;
    }

    /** Used by the separate HUD capture to share the same scoped Display dimensions. */
    public static void setLogicalGuiResolutionActive(boolean active) {
        logicalGuiResolutionActive = active;
    }

    /**
     * Minecraft computes drawScreen's hover coordinates before our FBO capture starts.
     * Scale those already-GUI-space coordinates from the desktop's old ScaledResolution
     * to the logical VR screen dimensions, without touching the input event queue.
     */
    public static int mapMouseXToLogicalScreen(int mouseX, int logicalWidth) {
        if (!capturing || savedScaledGuiWidth <= 0 || logicalWidth <= 0) return mouseX;
        return Math.round(mouseX * (logicalWidth / (float) savedScaledGuiWidth));
    }

    public static int mapMouseYToLogicalScreen(int mouseY, int logicalHeight) {
        if (!capturing || savedScaledGuiHeight <= 0 || logicalHeight <= 0) return mouseY;
        return Math.round(mouseY * (logicalHeight / (float) savedScaledGuiHeight));
    }

    /** Called once at the start of every render tick; allows exactly one clear per frame. */
    public static void newFrame() {
        frameId++;
    }

    private VRGuiRenderer() {}

    public static void checkSize(int w, int h) {
        if (w <= 0) w = 854;
        if (h <= 0) h = 480;
        if (capturing && (width != w || height != h)) end();
        if (fbo == 0 || width != w || height != h) {
            int oldFbo = fbo;
            int oldTexture = texture;
            int oldDepthRb = depthRb;
            int restoreFbo = GLStateManager.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int restoreTexture = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int restoreRenderbuffer = GLStateManager.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);

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

            if (guiFramebuffer == null) guiFramebuffer = new VRGuiFramebuffer(width, height);
            guiFramebuffer.update(fbo, texture, depthRb, width, height);

            // Resource allocation must not unbind another renderer's framebuffer or leave deleted attachments bound.
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, restoreFbo == oldFbo ? 0 : restoreFbo);
            GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER, restoreRenderbuffer == oldDepthRb ? 0 : restoreRenderbuffer);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, restoreTexture == oldTexture ? 0 : restoreTexture);
            clearedFrameId = -1L;
            clearedAsMenu = false;
        }
        if (guiFramebuffer == null) guiFramebuffer = new VRGuiFramebuffer(width, height);
        guiFramebuffer.update(fbo, texture, depthRb, width, height);
    }

    public static void begin(int w, int h, boolean inMainMenu) {
        checkSize(w, h);
        Minecraft mc = Minecraft.getMinecraft();
        boolean startingCapture = !capturing;
        if (startingCapture) {
            previousFbo = GLStateManager.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            previousMatrixMode = GLStateManager.glGetInteger(GL11.GL_MATRIX_MODE);
            GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            captureGlStatePushed = true;

            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPushMatrix();
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPushMatrix();

            if (mc != null) {
                savedMinecraftFramebuffer = OpenXRRuntime.getMinecraftFramebuffer(mc);
                OpenXRRuntime.setMinecraftFramebuffer(mc, guiFramebuffer);
                useLogicalGuiResolution(mc);
                setupLogicalGuiProjection(mc);
            }
        }

        capturing = true;
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glViewport(0, 0, width, height);

        // Never inherit a scissor rectangle from the desktop/world/Angelica render pass.
        // Individual GUI widgets can enable their own clipping during this capture.
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);

        // A nested begin must not erase pixels already drawn by GuiScreen / NEI.
        if (startingCapture || clearedFrameId != frameId || clearedAsMenu != inMainMenu) {
            GLStateManager.glDepthMask(true);
            GLStateManager.glColorMask(true, true, true, true);
            if (inMainMenu) GLStateManager.glClearColor(0.06F, 0.07F, 0.10F, 1.0F);
            else GLStateManager.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GLStateManager.glClearDepth(1.0D);
            GLStateManager.glClearStencil(0);
            GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
            clearedFrameId = frameId;
            clearedAsMenu = inMainMenu;
        }

        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void end() {
        if (!capturing) return;
        capturing = false;
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.glFlush();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && savedMinecraftFramebuffer != null) {
            OpenXRRuntime.setMinecraftFramebuffer(mc, savedMinecraftFramebuffer);
            savedMinecraftFramebuffer = null;
        }
        restoreWindowResolution(mc);

        if (captureGlStatePushed) {
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPopMatrix();
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPopMatrix();
            GLStateManager.glMatrixMode(previousMatrixMode);
            GLStateManager.glPopAttrib();
            captureGlStatePushed = false;
        }

        // FBO bindings are not included in glPushAttrib.
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
        previousFbo = 0;
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

    /** True while a GUI screen is being laid out/rendered on the virtual GUI canvas. */
    public static boolean isLogicalGuiResolutionActive() {
        return logicalGuiResolutionActive;
    }

    public static void destroy() {
        if (capturing) end();
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
        captureGlStatePushed = false;
        Minecraft mc = Minecraft.getMinecraft();
        restoreWindowResolution(mc);
        savedMinecraftFramebuffer = null;
    }
}
