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
    private static int mouseInputScopeDepth = 0;
    private static long frameId = 0L;
    private static final int GUI_CAPTURE_FPS = Integer.getInteger("gtnhvr.guiCaptureFps", 60);
    private static final long GUI_CAPTURE_INTERVAL_NANOS = GUI_CAPTURE_FPS > 0
            ? 1_000_000_000L / GUI_CAPTURE_FPS : 0L;
    private static long lastCaptureUpdateNanos;
    private static net.minecraft.client.gui.GuiScreen lastCaptureUpdateScreen;

    // A scoped stack keeps Minecraft's display dimensions, ScaledResolution, Display and GL
    // viewport/scissor calculations in the same coordinate space during GUI init and drawing.
    // The oldest entry always preserves the real physical window size for pointer conversion.
    private static final java.util.ArrayDeque<ResolutionScope> RESOLUTION_SCOPES =
            new java.util.ArrayDeque<>();

    private static VRGuiFramebuffer guiFramebuffer = null;

    // Remember the last screen initialized for the logical canvas. Some third-party screens
    // mutate their width/height during draw; re-running initGui every capture frame causes
    // expensive AE2/NEI reinitialization and transient blank/loading-looking frames.
    private static net.minecraft.client.gui.GuiScreen lastLogicalGuiScreen = null;
    private static int lastLogicalGuiWidth = -1;
    private static int lastLogicalGuiHeight = -1;

    // Maximum target width. Resolution is uniformly downscaled only when the desktop window
    // exceeds this width; small windows are never upscaled. Height follows the actual window
    // aspect ratio, so 4:3, 16:10 and ultrawide GUIs do not get letterboxed or distorted.
    private static final int VR_GUI_MAX_WIDTH = 1920;
    private static final int VR_GUI_FALLBACK_HEIGHT = 1080;

    public static int getCaptureWidth() {
        int physicalWidth = getPhysicalWindowWidth();
        if (OpenXRRuntime.isSessionRunning()) {
            return Math.max(1, Math.min(VR_GUI_MAX_WIDTH,
                    physicalWidth > 0 ? physicalWidth : VR_GUI_MAX_WIDTH));
        }
        return physicalWidth > 0 ? physicalWidth : VR_GUI_MAX_WIDTH;
    }

    public static int getCaptureHeight() {
        int physicalWidth = getPhysicalWindowWidth();
        int physicalHeight = getPhysicalWindowHeight();
        if (OpenXRRuntime.isSessionRunning()) {
            if (physicalWidth > 0 && physicalHeight > 0) {
                // width/physicalWidth is the single uniform scale s, clamped to s <= 1 above.
                return Math.max(1, Math.round(getCaptureWidth() * (physicalHeight / (float) physicalWidth)));
            }
            return VR_GUI_FALLBACK_HEIGHT;
        }
        return physicalHeight > 0 ? physicalHeight : VR_GUI_FALLBACK_HEIGHT;
    }

    /** The actual desktop/window dimensions, even while a logical GUI scope is active. */
    public static int getPhysicalWindowWidth() {
        ResolutionScope outermost = RESOLUTION_SCOPES.peekLast();
        if (outermost != null && outermost.displayWidth > 0) return outermost.displayWidth;
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.displayWidth > 0 ? mc.displayWidth : VR_GUI_WIDTH;
    }

    public static int getPhysicalWindowHeight() {
        ResolutionScope outermost = RESOLUTION_SCOPES.peekLast();
        if (outermost != null && outermost.displayHeight > 0) return outermost.displayHeight;
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.displayHeight > 0 ? mc.displayHeight : VR_GUI_FALLBACK_HEIGHT;
    }

    /**
     * Temporarily switches only GUI initialization/rendering to the active virtual target.
     * Nested scopes restore the dimensions and logical-resolution flag they found on entry.
     */
    public static void beginLogicalResolutionScope(Minecraft mc, int targetWidth, int targetHeight) {
        if (mc == null || targetWidth <= 0 || targetHeight <= 0) return;
        RESOLUTION_SCOPES.push(new ResolutionScope(mc, mc.displayWidth, mc.displayHeight,
                logicalGuiResolutionActive));
        mc.displayWidth = targetWidth;
        mc.displayHeight = targetHeight;
        logicalGuiResolutionActive = true;
    }

    public static void endLogicalResolutionScope(Minecraft mc) {
        if (RESOLUTION_SCOPES.isEmpty()) return;
        ResolutionScope scope = RESOLUTION_SCOPES.pop();
        Minecraft targetMc = scope.minecraft != null ? scope.minecraft : mc;
        if (targetMc != null) {
            targetMc.displayWidth = scope.displayWidth;
            targetMc.displayHeight = scope.displayHeight;
        }
        logicalGuiResolutionActive = scope.previousLogicalResolutionActive;
    }

    /**
     * Wraps all GUI input in a temporary virtual pixel space. Mouse getter mixins scale each raw
     * physical event to the same target dimensions that mc.displayWidth/Height expose here.
     * The caller must use try/finally so a mod's exception cannot leak these dimensions.
     */
    public static void beginMouseInputScope(Minecraft mc) {
        if (mc == null || !OpenXRRuntime.isSessionRunning()) return;
        beginLogicalResolutionScope(mc, getCaptureWidth(), getCaptureHeight());
        mouseInputScopeDepth++;
    }

    public static void endMouseInputScope(Minecraft mc) {
        if (mouseInputScopeDepth <= 0) return;
        mouseInputScopeDepth--;
        endLogicalResolutionScope(mc);
    }

    public static int mapPhysicalMouseEventXToLogicalCanvas(int eventX) {
        if (!OpenXRRuntime.isSessionRunning() || !logicalGuiResolutionActive) return eventX;
        int physicalWidth = getPhysicalWindowWidth();
        int targetWidth = getCaptureWidth();
        if (physicalWidth <= 0 || targetWidth <= 0) return eventX;
        return Math.round(eventX * (targetWidth / (float) physicalWidth));
    }

    public static int mapPhysicalMouseEventYToLogicalCanvas(int eventY) {
        if (!OpenXRRuntime.isSessionRunning() || !logicalGuiResolutionActive) return eventY;
        int physicalHeight = getPhysicalWindowHeight();
        int targetHeight = getCaptureHeight();
        if (physicalHeight <= 0 || targetHeight <= 0) return eventY;
        return Math.round(eventY * (targetHeight / (float) physicalHeight));
    }

    public static int mapDesktopMouseXToLogicalScreen(int mouseX) {
        if (!OpenXRRuntime.isSessionRunning() || capturing) return mouseX;
        int physicalWidth = getPhysicalScaledGuiWidth();
        int logicalWidth = getLogicalGuiWidth();
        if (physicalWidth <= 0 || logicalWidth <= 0) return mouseX;
        return Math.round(mouseX * (logicalWidth / (float) physicalWidth));
    }

    public static int mapDesktopMouseYToLogicalScreen(int mouseY) {
        if (!OpenXRRuntime.isSessionRunning() || capturing) return mouseY;
        int physicalHeight = getPhysicalScaledGuiHeight();
        int logicalHeight = getLogicalGuiHeight();
        if (physicalHeight <= 0 || logicalHeight <= 0) return mouseY;
        return Math.round(mouseY * (logicalHeight / (float) physicalHeight));
    }

    private static final class ResolutionScope {
        private final Minecraft minecraft;
        private final int displayWidth;
        private final int displayHeight;
        private final boolean previousLogicalResolutionActive;

        private ResolutionScope(Minecraft minecraft, int displayWidth, int displayHeight,
                                boolean previousLogicalResolutionActive) {
            this.minecraft = minecraft;
            this.displayWidth = displayWidth;
            this.displayHeight = displayHeight;
            this.previousLogicalResolutionActive = previousLogicalResolutionActive;
        }
    }

    /** Scaled coordinate dimensions of the virtual canvas, using its physical-window aspect. */
    public static int getLogicalGuiWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? VR_GUI_WIDTH
                : new net.minecraft.client.gui.ScaledResolution(mc, getCaptureWidth(), getCaptureHeight()).getScaledWidth();
    }

    public static int getLogicalGuiHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? VR_GUI_FALLBACK_HEIGHT
                : new net.minecraft.client.gui.ScaledResolution(mc, getCaptureWidth(), getCaptureHeight()).getScaledHeight();
    }

    /** Physical logical dimensions used only to remap desktop mouse coordinates to a VR screen. */
    public static int getPhysicalScaledGuiWidth() {
        int width = getPhysicalWindowWidth();
        int height = getPhysicalWindowHeight();
        return getPhysicalScaledGuiDimension(width, height);
    }

    public static int getPhysicalScaledGuiHeight() {
        int width = getPhysicalWindowWidth();
        int height = getPhysicalWindowHeight();
        return getPhysicalScaledGuiDimension(height, width);
    }

    private static int getPhysicalScaledGuiDimension(int primary, int secondary) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || primary <= 0 || secondary <= 0) return Math.max(1, primary);
        int limit = mc.gameSettings == null ? 0 : mc.gameSettings.guiScale;
        if (limit == 0) limit = 1000;
        int factor = 1;
        while (factor < limit
                && getPhysicalWindowWidth() / (factor + 1) >= 320
                && getPhysicalWindowHeight() / (factor + 1) >= 240) {
            factor++;
        }
        return Math.max(1, (int) Math.ceil(primary / (double) factor));
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

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        if (screen == null) {
            if (mc.currentScreen == null) {
                lastLogicalGuiScreen = null;
                lastLogicalGuiWidth = -1;
                lastLogicalGuiHeight = -1;
            }
            return;
        }

        // Offscreen or replaced screens may finish initGui after a nested GuiOpen event. Only the
        // currently active screen is allowed to replace this lifecycle marker.
        if (mc.currentScreen != screen) return;

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
     * the session started. Normal screen transitions get their logical size through the scoped
     * setWorldAndResolution argument overrides in MixinGuiScreen.
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
     * Display dimensions are changed only inside explicit resolution scopes and restored on exit.
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

    /**
     * When a GUI is open, cap expensive GUI capture updates without changing the HMD/world rate.
     * The last complete FBO image remains visible on skipped frames. Set
     * -Dgtnhvr.guiCaptureFps=0 to disable the cap; default is 60 updates/second.
     */
    public static boolean shouldUpdateCaptureForScreen(net.minecraft.client.gui.GuiScreen screen) {
        if (!OpenXRRuntime.isSessionRunning() || screen == null || GUI_CAPTURE_INTERVAL_NANOS <= 0L) {
            lastCaptureUpdateScreen = null;
            lastCaptureUpdateNanos = 0L;
            return true;
        }

        long now = System.nanoTime();
        if (screen != lastCaptureUpdateScreen || lastCaptureUpdateNanos == 0L
                || now - lastCaptureUpdateNanos >= GUI_CAPTURE_INTERVAL_NANOS) {
            lastCaptureUpdateScreen = screen;
            lastCaptureUpdateNanos = now;
            return true;
        }

        VRGuiProfiler.guiCaptureSkipped();
        return false;
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
