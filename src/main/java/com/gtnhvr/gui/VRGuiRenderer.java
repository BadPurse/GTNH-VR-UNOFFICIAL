package com.gtnhvr.gui;

import com.gtnhvr.OpenXRRuntime;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;

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

        if (screen == null) {
            lastLogicalGuiScreen = null;
            lastLogicalGuiWidth = -1;
            lastLogicalGuiHeight = -1;
            return;
        }

        int expectedWidth = getLogicalGuiWidth();
        int expectedHeight = getLogicalGuiHeight();
        if (screen.width == expectedWidth && screen.height == expectedHeight) {
            lastLogicalGuiScreen = screen;
            lastLogicalGuiWidth = expectedWidth;
            lastLogicalGuiHeight = expectedHeight;
        } else {
            // Do not mark a screen as normalized if a third-party init path supplied desktop
            // dimensions. ensureLogicalGuiScreen will correct this one time before drawing.
            lastLogicalGuiScreen = null;
            lastLogicalGuiWidth = -1;
            lastLogicalGuiHeight = -1;
        }
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

        int logicalWidth = getLogicalGuiWidth();
        int logicalHeight = getLogicalGuiHeight();
        if (lastLogicalGuiScreen == screen
                && lastLogicalGuiWidth == logicalWidth
                && lastLogicalGuiHeight == logicalHeight) {
            return;
        }

        // Remember the intended dimensions before calling into mod GUI initialization, which may
        // post events or cause a nested render callback. A correctly initialized screen will be
        // confirmed by noteLogicalGuiScreen at the end of setWorldAndResolution.
        lastLogicalGuiScreen = null;
        lastLogicalGuiWidth = logicalWidth;
        lastLogicalGuiHeight = logicalHeight;
        if (screen.width != logicalWidth || screen.height != logicalHeight) {
            screen.setWorldAndResolution(mc, logicalWidth, logicalHeight);
        } else {
            lastLogicalGuiScreen = screen;
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

        VRGuiRenderPipeline.ensureTarget(VRGuiRenderPipeline.Pass.SCREEN, w, h);
        fbo = VRGuiRenderPipeline.getFramebufferId(VRGuiRenderPipeline.Pass.SCREEN);
        texture = VRGuiRenderPipeline.getTextureId(VRGuiRenderPipeline.Pass.SCREEN);
        depthRb = VRGuiRenderPipeline.getDepthBufferId(VRGuiRenderPipeline.Pass.SCREEN);
        width = VRGuiRenderPipeline.getWidth(VRGuiRenderPipeline.Pass.SCREEN);
        height = VRGuiRenderPipeline.getHeight(VRGuiRenderPipeline.Pass.SCREEN);
        guiFramebuffer = VRGuiRenderPipeline.getTargetFramebuffer(VRGuiRenderPipeline.Pass.SCREEN);
    }

    public static void begin(int w, int h, boolean inMainMenu) {
        if (!OpenXRRuntime.isSessionRunning()) return;
        checkSize(w, h);
        if (capturing) return;

        // Screen initialization is managed on ClientTick before rendering begins. Never call
        // setWorldAndResolution from DrawScreenEvent.Pre or from inside a GUI render pass.
        boolean began = VRGuiRenderPipeline.begin(VRGuiRenderPipeline.Pass.SCREEN, inMainMenu);
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

    public static void destroy() {
        if (capturing) end();
        VRGuiRenderPipeline.destroyTarget(VRGuiRenderPipeline.Pass.SCREEN);
        fbo = 0;
        texture = 0;
        depthRb = 0;
        width = 0;
        height = 0;
        guiFramebuffer = null;
        capturing = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (!OpenXRRuntime.isSessionRunning()) restoreDesktopGuiResolution(mc);
    }
}
