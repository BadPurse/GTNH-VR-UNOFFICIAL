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
    private static long frameId = 0L;

    private static VRGuiFramebuffer guiFramebuffer = null;

    // Remember the last screen initialized for the logical canvas. Some third-party screens
    // mutate their width/height during draw; re-running initGui every capture frame causes
    // expensive AE2/NEI reinitialization and transient blank/loading-looking frames.
    private static net.minecraft.client.gui.GuiScreen lastLogicalGuiScreen = null;
    private static int lastLogicalGuiWidth = -1;
    private static int lastLogicalGuiHeight = -1;

    // Same fixed GUI target as Visor's VRGuiManagerImpl. The screen canvas must not
    // inherit the desktop aspect ratio or the custom 2388x1668 default, which is not 16:9.
    private static final int VR_GUI_WIDTH = 1920;
    private static final int VR_GUI_HEIGHT = 1080;

    public static int getCaptureWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning()) return VR_GUI_WIDTH;
        return mc != null && mc.displayWidth > 0 ? mc.displayWidth : VR_GUI_WIDTH;
    }

    public static int getCaptureHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning()) return VR_GUI_HEIGHT;
        return mc != null && mc.displayHeight > 0 ? mc.displayHeight : VR_GUI_HEIGHT;
    }

    /** The physical window remains physical even while VR GUI targets are active. */
    public static int getPhysicalWindowWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.displayWidth > 0 ? mc.displayWidth : VR_GUI_WIDTH;
    }

    public static int getPhysicalWindowHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.displayHeight > 0 ? mc.displayHeight : VR_GUI_HEIGHT;
    }

    /** Scaled coordinate dimensions of the fixed virtual canvas. */
    public static int getLogicalGuiWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? VR_GUI_WIDTH
                : new net.minecraft.client.gui.ScaledResolution(mc, VR_GUI_WIDTH, VR_GUI_HEIGHT).getScaledWidth();
    }

    public static int getLogicalGuiHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? VR_GUI_HEIGHT
                : new net.minecraft.client.gui.ScaledResolution(mc, VR_GUI_WIDTH, VR_GUI_HEIGHT).getScaledHeight();
    }

    public static boolean shouldUseVirtualResolution() {
        return OpenXRRuntime.isSessionRunning();
    }

    /**
     * Called from GuiScreen.setWorldAndResolution after the logical dimensions have been applied.
     * Tracking screen identity prevents reinitializing AE2/NEI and machine screens during drawing.
     */
    public static void noteLogicalGuiScreen(net.minecraft.client.gui.GuiScreen screen) {
        if (!OpenXRRuntime.isSessionRunning()) return;
        lastLogicalGuiScreen = screen;
        lastLogicalGuiWidth = screen == null ? -1 : screen.width;
        lastLogicalGuiHeight = screen == null ? -1 : screen.height;
    }

    /**
     * Handles only the one-time transition into VR for a screen which was already open before
     * the session started. Normal screen transitions get their logical size through
     * MixinScaledResolution during Minecraft's ordinary setWorldAndResolution call.
     */
    public static void ensureLogicalGuiScreen(Minecraft mc) {
        if (mc == null || !OpenXRRuntime.isSessionRunning()) return;
        net.minecraft.client.gui.GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            lastLogicalGuiScreen = null;
            lastLogicalGuiWidth = -1;
            lastLogicalGuiHeight = -1;
            return;
        }
        if (lastLogicalGuiScreen == screen) return;

        int logicalWidth = getLogicalGuiWidth();
        int logicalHeight = getLogicalGuiHeight();
        if (screen.width != logicalWidth || screen.height != logicalHeight) {
            screen.setWorldAndResolution(mc, logicalWidth, logicalHeight);
        } else {
            noteLogicalGuiScreen(screen);
        }
    }

    /**
     * If VR ends while a virtual screen is open, restore its real desktop layout exactly once.
     * No Minecraft.displayWidth/Height fields are ever changed by this pipeline.
     */
    public static void restoreDesktopGuiResolution(Minecraft mc) {
        if (mc == null || mc.currentScreen == null) {
            lastLogicalGuiScreen = null;
            lastLogicalGuiWidth = -1;
            lastLogicalGuiHeight = -1;
            return;
        }
        if (lastLogicalGuiScreen == mc.currentScreen) {
            net.minecraft.client.gui.ScaledResolution physical =
                    new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int physicalWidth = physical.getScaledWidth();
            int physicalHeight = physical.getScaledHeight();
            if (mc.currentScreen.width != physicalWidth || mc.currentScreen.height != physicalHeight) {
                mc.currentScreen.setWorldAndResolution(mc, physicalWidth, physicalHeight);
            }
        }
        lastLogicalGuiScreen = null;
        lastLogicalGuiWidth = -1;
        lastLogicalGuiHeight = -1;
    }

    /** Marker for code that needs to know whether a virtual 2D pass currently owns the target. */
    public static void setLogicalGuiResolutionActive(boolean active) {
        logicalGuiResolutionActive = active;
    }

    public static boolean isLogicalGuiResolutionActive() {
        return logicalGuiResolutionActive;
    }

    /**
     * Input coordinates are converted by Minecraft itself because ScaledResolution is virtualized
     * at construction time. Kept for compatibility with older callers; do not rescale a second time.
     */
    public static int mapMouseXToLogicalScreen(int mouseX, int logicalWidth) {
        return mouseX;
    }

    public static int mapMouseYToLogicalScreen(int mouseY, int logicalHeight) {
        return mouseY;
    }

    /** Called once at the start of every render tick; allows diagnostics to associate captures. */
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
        }
        if (guiFramebuffer == null) guiFramebuffer = new VRGuiFramebuffer(width, height);
        guiFramebuffer.update(fbo, texture, depthRb, width, height);
    }

    public static void begin(int w, int h, boolean inMainMenu) {
        if (!OpenXRRuntime.isSessionRunning()) return;
        checkSize(w, h);
        if (capturing) return;

        Minecraft mc = Minecraft.getMinecraft();
        ensureLogicalGuiScreen(mc);
        if (guiFramebuffer == null) guiFramebuffer = new VRGuiFramebuffer(width, height);
        guiFramebuffer.update(fbo, texture, depthRb, width, height);

        boolean began = VRGuiRenderPipeline.begin(VRGuiRenderPipeline.Pass.SCREEN,
                fbo, width, height, inMainMenu, guiFramebuffer);
        if (!began) return;

        capturing = true;
    }

    public static void end() {
        if (!capturing) return;
        try {
            VRGuiRenderPipeline.end(VRGuiRenderPipeline.Pass.SCREEN);
        } finally {
            capturing = false;
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
        Minecraft mc = Minecraft.getMinecraft();
        if (!OpenXRRuntime.isSessionRunning()) {
            restoreDesktopGuiResolution(mc);
        }
    }
}
