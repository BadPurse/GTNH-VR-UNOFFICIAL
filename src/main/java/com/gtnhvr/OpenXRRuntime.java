package com.gtnhvr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.openxr.*;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.openxr.KHROpenGLEnable.*;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * OpenXR runtime & stereoscopic rendering pipeline.
 * Connects Minecraft's SDL3/OpenGL window to OpenXR (VDXR / Meta Quest / SteamVR)
 * and provides dual-pass stereoscopic 3D rendering with authentic optical FOV matrices.
 */
@Lwjgl3Aware
public final class OpenXRRuntime {
    private static boolean initialized;
    private static boolean sessionRunning;
    /** True only between xrBeginSession (state READY) and STOPPING/EXITING/LOSS_PENDING. */
    private static boolean sessionBegun;
    private static float seatedBaselineY = Float.NaN;

    /** Forgets the sitting head height so the next frame becomes the seated reference. */
    public static void resetSeatedBaseline() {
        seatedBaselineY = Float.NaN;
    }
    private static XrInstance instance;
    private static long systemId;
    private static XrSession session;
    private static XrSpace localSpace;
    private static int viewCount;
    private static XrViewConfigurationView.Buffer viewConfig;
    private static EyeSwapchain[] eyes;
    private static VREyeFramebuffer[] eyeFramebuffers;
    private static XrFrameState frameState;
    private static XrViewState viewState;
    private static XrView.Buffer views;
    private static boolean frameBegun;
    private static XrPosef headPose;

    private static boolean vrStereoActive;
    private static boolean renderingEyePass;
    private static int currentEyeIndex;
    private static boolean isStageSpace;

    private static final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer menuModelViewBuffer = BufferUtils.createFloatBuffer(16);

    private OpenXRRuntime() {}

    public static synchronized boolean initialize() {
        if (initialized && sessionRunning) return true;
        try {
            GTNHVRLog.info("OpenXR: starting runtime initialization");
            if (com.gtnhvr.settings.VRClientSettings.graphicsApi == com.gtnhvr.settings.GraphicsApi.VULKAN) {
                GTNHVRLog.warn("Graphics API VULKAN requested, but this build submits frames through OpenGL only; using OpenGL");
            }
            try {
                org.lwjgl.openxr.XR.create();
            } catch (IllegalStateException ignored) {
                // OpenXR classes already initialized
            }
            createInstance();
            acquireSystem();
            createSessionFromMinecraftGL();
            createReferenceSpace();
            createViewsAndSwapchains();
            VRControllerHandler.getInstance().init(instance, session);
            initialized = true;
            GTNHVRLog.info("OpenXR: session layer ready (%d views)", viewCount);
            return true;
        } catch (Throwable t) {
            GTNHVRLog.warn("OpenXR initialization failed: %s", t);
            shutdown();
            return false;
        }
    }

    private static void createInstance() {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer extensions = stack.mallocPointer(1);
            extensions.put(stack.UTF8(XR_KHR_OPENGL_ENABLE_EXTENSION_NAME)).flip();

            XrApplicationInfo app = XrApplicationInfo.calloc(stack);
            app.applicationName(stack.UTF8("GTNHVR"));
            app.applicationVersion(1);
            app.engineName(stack.UTF8("GTNH / lwjgl3ify"));
            app.engineVersion(1);
            app.apiVersion(XR_MAKE_VERSION(1, 0, 0));

            XrInstanceCreateInfo ci = XrInstanceCreateInfo.calloc(stack)
                    .type(XR_TYPE_INSTANCE_CREATE_INFO)
                    .applicationInfo(app)
                    .enabledExtensionNames(extensions);

            PointerBuffer out = stack.mallocPointer(1);
            check(xrCreateInstance(ci, out), "xrCreateInstance");
            instance = new XrInstance(out.get(0), ci);

            XrInstanceProperties props = XrInstanceProperties.calloc(stack).type(XR_TYPE_INSTANCE_PROPERTIES);
            check(xrGetInstanceProperties(instance, props), "xrGetInstanceProperties");
            GTNHVRLog.info("OpenXR runtime: %s %d.%d.%d", props.runtimeNameString(),
                    XR_VERSION_MAJOR(props.runtimeVersion()), XR_VERSION_MINOR(props.runtimeVersion()),
                    XR_VERSION_PATCH(props.runtimeVersion()));
        }
    }

    private static void acquireSystem() {
        try (MemoryStack stack = stackPush()) {
            XrSystemGetInfo info = XrSystemGetInfo.calloc(stack)
                    .type(XR_TYPE_SYSTEM_GET_INFO)
                    .formFactor(XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);
            LongBuffer out = stack.longs(0);
            check(xrGetSystem(instance, info, out), "xrGetSystem");
            systemId = out.get(0);
            GTNHVRLog.info("OpenXR HMD system ID: %d", systemId);
        }
    }

    private static void createSessionFromMinecraftGL() {
        long window = SDL_GL_GetCurrentWindow();
        long glContext = SDL_GL_GetCurrentContext();
        if (window == 0 || glContext == 0) {
            throw new IllegalStateException("Minecraft SDL/OpenGL context is not current");
        }

        int props = SDL_GetWindowProperties(window);
        long hdc = SDLProperties.SDL_GetPointerProperty(props, SDL_PROP_WINDOW_WIN32_HDC_POINTER, 0L);
        if (hdc == 0) {
            throw new IllegalStateException("SDL did not expose a Win32 HDC for Minecraft window");
        }

        try (MemoryStack stack = stackPush()) {
            XrGraphicsRequirementsOpenGLKHR req = XrGraphicsRequirementsOpenGLKHR.calloc(stack)
                    .type(XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_KHR);
            check(xrGetOpenGLGraphicsRequirementsKHR(instance, systemId, req),
                    "xrGetOpenGLGraphicsRequirementsKHR");

            XrGraphicsBindingOpenGLWin32KHR binding = XrGraphicsBindingOpenGLWin32KHR.calloc(stack)
                    .type(XR_TYPE_GRAPHICS_BINDING_OPENGL_WIN32_KHR)
                    .hDC(hdc)
                    .hGLRC(glContext);

            XrSessionCreateInfo sci = XrSessionCreateInfo.calloc(stack)
                    .type(XR_TYPE_SESSION_CREATE_INFO)
                    .next(binding.address())
                    .systemId(systemId);

            PointerBuffer out = stack.mallocPointer(1);
            check(xrCreateSession(instance, sci, out), "xrCreateSession");
            session = new XrSession(out.get(0), instance);
            sessionRunning = true;
            GTNHVRLog.info("OpenXR session bound to Minecraft OpenGL context");
        }
    }

    public static boolean isStageSpace() {
        return isStageSpace;
    }

    private static void createReferenceSpace() {
        try (MemoryStack stack = stackPush()) {
            int refType = XR_REFERENCE_SPACE_TYPE_STAGE;
            IntBuffer spaceCount = stack.ints(0);
            boolean stageSupported = false;
            if (xrEnumerateReferenceSpaces(session, spaceCount, null) == XR_SUCCESS && spaceCount.get(0) > 0) {
                IntBuffer spaces = stack.mallocInt(spaceCount.get(0));
                xrEnumerateReferenceSpaces(session, spaceCount, spaces);
                for (int i = 0; i < spaceCount.get(0); i++) {
                    if (spaces.get(i) == XR_REFERENCE_SPACE_TYPE_STAGE) {
                        stageSupported = true;
                        break;
                    }
                }
            }
            if (!stageSupported) {
                refType = XR_REFERENCE_SPACE_TYPE_LOCAL;
            }
            isStageSpace = stageSupported;
            GTNHVRLog.info("Using OpenXR reference space: %s", stageSupported ? "STAGE (floor origin)" : "LOCAL (head origin)");

            XrReferenceSpaceCreateInfo ci = XrReferenceSpaceCreateInfo.calloc(stack)
                    .type(XR_TYPE_REFERENCE_SPACE_CREATE_INFO)
                    .referenceSpaceType(refType);
            ci.poseInReferenceSpace().orientation().w(1.0f);

            PointerBuffer out = stack.mallocPointer(1);
            check(xrCreateReferenceSpace(session, ci, out), "xrCreateReferenceSpace");
            localSpace = new XrSpace(out.get(0), session);
        }
    }

    private static void createViewsAndSwapchains() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            check(xrEnumerateViewConfigurationViews(instance, systemId,
                    XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, null),
                    "xrEnumerateViewConfigurationViews(count)");
            viewCount = count.get(0);
            if (viewCount != 2) {
                GTNHVRLog.warn("Unexpected view count: %d, expected 2", viewCount);
            }

            viewConfig = XrViewConfigurationView.calloc(viewCount);
            for (int i = 0; i < viewCount; i++) {
                viewConfig.get(i).type(XR_TYPE_VIEW_CONFIGURATION_VIEW);
            }
            check(xrEnumerateViewConfigurationViews(instance, systemId,
                    XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, viewConfig),
                    "xrEnumerateViewConfigurationViews(views)");

            IntBuffer fmtCount = stack.ints(0);
            check(xrEnumerateSwapchainFormats(session, fmtCount, null), "xrEnumerateSwapchainFormats(count)");
            LongBuffer formats = stack.mallocLong(fmtCount.get(0));
            check(xrEnumerateSwapchainFormats(session, fmtCount, formats), "xrEnumerateSwapchainFormats(data)");

            long chosenFormat = chooseColorFormat(formats);
            GTNHVRLog.info("OpenXR selected swapchain format: 0x%X", chosenFormat);

            eyes = new EyeSwapchain[viewCount];
            eyeFramebuffers = new VREyeFramebuffer[viewCount];
            for (int i = 0; i < viewCount; i++) {
                XrViewConfigurationView v = viewConfig.get(i);
                float renderScale = Math.max(0.5F, Math.min(2.0F, com.gtnhvr.settings.VRClientSettings.renderScaleFactor));
                int w = Math.max(64, Math.round(v.recommendedImageRectWidth() * renderScale));
                int h = Math.max(64, Math.round(v.recommendedImageRectHeight() * renderScale));
                if (v.maxImageRectWidth() > 0) w = Math.min(w, v.maxImageRectWidth());
                if (v.maxImageRectHeight() > 0) h = Math.min(h, v.maxImageRectHeight());
                int samples = v.recommendedSwapchainSampleCount();
                if (samples <= 0) samples = 1;
                eyes[i] = EyeSwapchain.create(session, chosenFormat, w, h, samples, stack, i);
                eyeFramebuffers[i] = new VREyeFramebuffer(i, w, h);
                eyeFramebuffers[i].updateFromSwapchain(eyes[i].fbo, 0, 0, eyes[i].depthTex, w, h);
                GTNHVRLog.info("Created eye swapchain [%d]: %dx%d (samples=%d, depthTex=%d)", i, w, h, samples, eyes[i].depthTex);
            }

            frameState = XrFrameState.create();
            viewState = XrViewState.create();
            views = XrView.calloc(viewCount);
            for (int i = 0; i < viewCount; i++) {
                views.get(i).type(XR_TYPE_VIEW);
            }
            headPose = XrPosef.create();
            headPose.orientation().w(1.0f);
        }
    }

    private static long chooseColorFormat(LongBuffer formats) {
        long fallback = 0;
        for (int i = 0; i < formats.capacity(); i++) {
            long f = formats.get(i);
            if (fallback == 0) fallback = f;
            if (f == GL11.GL_RGBA8 || f == 0x8058) return f;
            if (f == 0x8C43) return f; // GL_SRGB8_ALPHA8
        }
        return fallback;
    }

    public static boolean prepareFrame() {
        if (com.gtnhvr.settings.VRClientSettings.vrPlayMode == com.gtnhvr.settings.VRPlayMode.DISABLED
                || session == null || !sessionRunning) return false;
        if (frameBegun) return frameState != null && frameState.shouldRender();

        pollEvents();
        if (!sessionRunning || !sessionBegun) return false;

        try (MemoryStack stack = stackPush()) {
            XrFrameWaitInfo wait = XrFrameWaitInfo.calloc(stack).type(XR_TYPE_FRAME_WAIT_INFO);
            frameState.type(XR_TYPE_FRAME_STATE);
            check(xrWaitFrame(session, wait, frameState), "xrWaitFrame");

            XrFrameBeginInfo begin = XrFrameBeginInfo.calloc(stack).type(XR_TYPE_FRAME_BEGIN_INFO);
            check(xrBeginFrame(session, begin), "xrBeginFrame");
            frameBegun = true;

            if (!frameState.shouldRender()) return false;

            XrViewLocateInfo locate = XrViewLocateInfo.calloc(stack)
                    .type(XR_TYPE_VIEW_LOCATE_INFO)
                    .viewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
                    .displayTime(frameState.predictedDisplayTime())
                    .space(localSpace);
            viewState.type(XR_TYPE_VIEW_STATE);
            for (int i = 0; i < views.capacity(); i++) views.get(i).type(XR_TYPE_VIEW);
            IntBuffer count = stack.ints(0);
            check(xrLocateViews(session, locate, viewState, count, views), "xrLocateViews");

            if (count.get(0) >= 2) {
                XrPosef pose = views.get(0).pose();
                headPose.position$().set(pose.position$());
                headPose.orientation().set(pose.orientation());
            }

            VRControllerHandler.getInstance().update(session, localSpace, frameState.predictedDisplayTime());
            return true;
        } catch (Throwable t) {
            GTNHVRLog.warn("OpenXR prepareFrame error: %s", t);
            return false;
        }
    }

    public static void beginFrame() {
        prepareFrame();
    }

    /**
     * Executes dual-pass stereoscopic world rendering.
     * Eye 0 (Left Eye) -> Eye 0 FBO with asymmetric optical projection & 6DoF camera
     * Eye 1 (Right Eye) -> Eye 1 FBO with asymmetric optical projection & 6DoF camera
     */
    public static void renderVRStereoWorld(EntityRenderer renderer, float partialTicks, long finishTimeNano) {
        if (session == null || !sessionRunning || eyes == null || eyes.length < 2) {
            renderingEyePass = true;
            try {
                renderer.renderWorld(partialTicks, finishTimeNano);
            } finally {
                renderingEyePass = false;
            }
            return;
        }

        if (!prepareFrame()) {
            renderingEyePass = true;
            try {
                renderer.renderWorld(partialTicks, finishTimeNano);
            } finally {
                renderingEyePass = false;
            }
            return;
        }

        // Radial hotbar textures are drawn once per frame, not once per eye.
        com.gtnhvr.gui.VRHotbarSelector.getInstance().prepareTextures(Minecraft.getMinecraft());

        Minecraft mc = Minecraft.getMinecraft();
        int desktopFbo = (mc.getFramebuffer() != null && mc.getFramebuffer().framebufferObject > 0)
                ? mc.getFramebuffer().framebufferObject : 0;
        int desktopW = (mc.getFramebuffer() != null && mc.getFramebuffer().framebufferWidth > 0)
                ? mc.getFramebuffer().framebufferWidth : (mc.displayWidth > 0 ? mc.displayWidth : 854);
        int desktopH = (mc.getFramebuffer() != null && mc.getFramebuffer().framebufferHeight > 0)
                ? mc.getFramebuffer().framebufferHeight : (mc.displayHeight > 0 ? mc.displayHeight : 480);

        boolean prevScissor = GLStateManager.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (prevScissor) {
            GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
        }

        IntBuffer prevViewport = BufferUtils.createIntBuffer(16);
        GLStateManager.glGetInteger(GL11.GL_VIEWPORT, prevViewport);

        net.minecraft.client.shader.Framebuffer origFb = getMinecraftFramebuffer(mc);

        renderingEyePass = true;
        vrStereoActive = true;
        try {
            Vector3f hmdPos = new Vector3f(headPose.position$().x(), headPose.position$().y(), headPose.position$().z());
            Quaternionf hmdRot = new Quaternionf(headPose.orientation().x(), headPose.orientation().y(), headPose.orientation().z(), headPose.orientation().w());

            boolean inGameMenu = (mc != null && mc.currentScreen != null);
            if (inGameMenu) {
                com.gtnhvr.gui.VRVirtualScreen.getInstance().updateScreenPlacement(false, hmdPos, hmdRot);
            } else {
                com.gtnhvr.gui.VRVirtualScreen.getInstance().resetOrientation();
            }

            int guiTex = com.gtnhvr.gui.VRGuiRenderer.getTextureId();
            int screenTex = (guiTex > 0) ? guiTex : ((mc.getFramebuffer() != null && mc.getFramebuffer().framebufferTexture > 0) ? mc.getFramebuffer().framebufferTexture : 0);
            int screenW = (guiTex > 0) ? com.gtnhvr.gui.VRGuiRenderer.getWidth() : (mc.displayWidth > 0 ? mc.displayWidth : 854);
            int screenH = (guiTex > 0) ? com.gtnhvr.gui.VRGuiRenderer.getHeight() : (mc.displayHeight > 0 ? mc.displayHeight : 480);

            // Refresh Angelica's culling graph so Left Eye calculates its own frustum occlusion
            try {
                if (mc != null && mc.renderGlobal instanceof com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt) {
                    ((com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt) mc.renderGlobal).angelica$scheduleTerrainUpdate();
                }
            } catch (Throwable ignored) {}

            // ---- PASS 0: LEFT EYE ----
            currentEyeIndex = 0;
            if (mc != null && eyeFramebuffers != null && eyeFramebuffers.length > 0 && eyeFramebuffers[0] != null) {
                setMinecraftFramebuffer(mc, eyeFramebuffers[0]);
            }
            resetCeleritasEyeMatrices();
            EyeSwapchain leftEye = eyes[0];
            leftEye.beginRender();
            try {
                GLStateManager.glViewport(0, 0, leftEye.width, leftEye.height);
                GLStateManager.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
                GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

                renderer.renderWorld(partialTicks, finishTimeNano);

                renderStereoOverlays(0, mc, hmdPos, hmdRot, screenTex, screenW, screenH, partialTicks);

                // Mirror Left Eye directly to desktop window framebuffer while swapchain texture is active
                if (com.gtnhvr.settings.VRClientSettings.mirrorEye == 0) {
                    blitToDesktop(leftEye, desktopFbo, desktopW, desktopH);
                }
            } finally {
                leftEye.endRender();
            }

            // Refresh Angelica's culling graph so Right Eye calculates its own frustum occlusion
            try {
                if (mc != null && mc.renderGlobal instanceof com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt) {
                    ((com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt) mc.renderGlobal).angelica$scheduleTerrainUpdate();
                }
            } catch (Throwable ignored) {}

            // ---- PASS 1: RIGHT EYE ----
            currentEyeIndex = 1;
            if (mc != null && eyeFramebuffers != null && eyeFramebuffers.length > 1 && eyeFramebuffers[1] != null) {
                setMinecraftFramebuffer(mc, eyeFramebuffers[1]);
            }
            resetCeleritasEyeMatrices();
            EyeSwapchain rightEye = eyes[1];
            rightEye.beginRender();
            try {
                GLStateManager.glViewport(0, 0, rightEye.width, rightEye.height);
                GLStateManager.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
                GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

                renderer.renderWorld(partialTicks, finishTimeNano);

                renderStereoOverlays(1, mc, hmdPos, hmdRot, screenTex, screenW, screenH, partialTicks);

                // Mirror Right Eye directly to desktop window framebuffer if mirrorEye == 1
                if (com.gtnhvr.settings.VRClientSettings.mirrorEye == 1) {
                    blitToDesktop(rightEye, desktopFbo, desktopW, desktopH);
                }
            } finally {
                rightEye.endRender();
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("VR stereo rendering error: %s", t);
        } finally {
            if (mc != null && origFb != null) {
                setMinecraftFramebuffer(mc, origFb);
            }
            renderingEyePass = false;
            vrStereoActive = false;

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, desktopFbo);
            GLStateManager.glViewport(prevViewport.get(0), prevViewport.get(1), prevViewport.get(2), prevViewport.get(3));
            if (prevScissor) {
                GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private static void renderStereoOverlays(int eyeIndex, Minecraft mc, Vector3f hmdPos, Quaternionf hmdRot, int screenTex, int screenW, int screenH, float partialTicks) {
        if (mc == null || eyes == null || eyeIndex < 0 || eyeIndex >= eyes.length) return;

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glPushMatrix();
        try {
            // Unbind any active shader program
            try {
                com.gtnewhorizons.angelica.glsm.GLStateManager.glUseProgram(0);
            } catch (Throwable ignored) {}

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, eyes[eyeIndex].fbo);
            GLStateManager.glViewport(0, 0, eyes[eyeIndex].width, eyes[eyeIndex].height);

            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glLoadMatrix(getEyeProjectionBuffer(eyeIndex, 0.05F, 100.0F));

            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            FloatBuffer mv = getMenuModelViewBuffer(eyeIndex);
            if (mv != null) {
                GLStateManager.glLoadMatrix(mv);
            }

            boolean inGameGui = (mc.currentScreen != null);
            if (!inGameGui) {
                // 1. Hands (solid player skin arms + held items)
                com.gtnhvr.gui.VRHandRenderer.render(eyeIndex, hmdPos, hmdRot);

                // 2. 3D Crosshair ('+' from Visor) in World Camera Space (zero offset)
                com.gtnhvr.gui.VRHandRenderer.renderCrosshair(mc, eyeIndex, partialTicks);

                // 2.5 Parabolic Teleport Arc & Physical Bow Trajectory Arc
                com.gtnhvr.gameplay.tasks.VRTaskTeleport.getInstance().renderTeleportArc();
                com.gtnhvr.gameplay.tasks.VRTaskBow.getInstance().renderTrajectoryArc();

                // 2.6 Mob Spawn Overlay (F7-style light level crosses in world space)
                com.gtnhvr.gui.VRMobSpawnOverlay.getInstance().render(partialTicks, eyeIndex);

                // 3. Clear depth for HUD and Hotbar
                GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);

                // 5. Floating HUD (health, hunger, armor, minimap with smooth Head-Lock)
                boolean hasHud = (com.gtnhvr.gui.VRHudRenderer.getTextureId() > 0 && !mc.gameSettings.hideGUI);
                if (hasHud) {
                    com.gtnhvr.gui.VRHudRenderer.render(eyeIndex, hmdPos, hmdRot);
                }

                // 6. Wrist Overlays (Inventory, Status, Map, World Info, Potions) - rendered purely by turning wrists, NO GRIP REQUIRED!
                com.gtnhvr.gui.VRWristInventory.getInstance().render();
                com.gtnhvr.gui.VRWristStatusOverlay.getInstance().render();
                com.gtnhvr.gui.VRWristMapOverlay.getInstance().render();
                com.gtnhvr.gui.VRWristInfoOverlay.getInstance().render();
                com.gtnhvr.gui.VRWristPotionOverlay.getInstance().render();

                // 7. Floating 3x3 Hotbar selector in front of controller hand (Side Grip)
                boolean hasHotbar = com.gtnhvr.gui.VRHotbarSelector.getInstance().isSelectorActive();
                if (hasHotbar) {
                    com.gtnhvr.gui.VRHotbarSelector.getInstance().render(eyeIndex, hmdPos);
                }

                // 6. In-block collision vignette (checks both player entity and physical 6-DoF HMD head position for Anti-XRay)
                boolean isHeadInside = false;
                if (com.gtnhvr.settings.VRClientSettings.antiXrayVignette && mc.theWorld != null && mc.thePlayer != null) {
                    if (mc.thePlayer.isEntityInsideOpaqueBlock()) {
                        isHeadInside = true;
                    } else if (headPose != null) {
                        net.minecraft.util.Vec3 eyeWorld = VRControllerHandler.getInstance().roomToWorld(hmdPos, 1.0F);
                        int bx = net.minecraft.util.MathHelper.floor_double(eyeWorld.xCoord);
                        int by = net.minecraft.util.MathHelper.floor_double(eyeWorld.yCoord);
                        int bz = net.minecraft.util.MathHelper.floor_double(eyeWorld.zCoord);
                        net.minecraft.block.Block block = mc.theWorld.getBlock(bx, by, bz);
                        if (block != null && !block.isAir(mc.theWorld, bx, by, bz)) {
                            net.minecraft.util.AxisAlignedBB aabb = block.getCollisionBoundingBoxFromPool(mc.theWorld, bx, by, bz);
                            if (aabb != null && aabb.isVecInside(eyeWorld)) {
                                isHeadInside = true;
                            } else if (block.isOpaqueCube()) {
                                isHeadInside = true;
                            }
                        }
                    }
                }
                if (isHeadInside) {
                    GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                    GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
                    GLStateManager.glDisable(GL11.GL_CULL_FACE);
                    GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
                    GLStateManager.glEnable(GL11.GL_BLEND);
                    GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GLStateManager.glColor4f(0.01F, 0.01F, 0.02F, 0.98F);

                    GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
                    GLStateManager.glPushMatrix();
                    GLStateManager.glLoadIdentity();
                    GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                    GLStateManager.glPushMatrix();
                    GLStateManager.glLoadIdentity();

                    GLStateManager.glBegin(GL11.GL_QUADS);
                    GLStateManager.glVertex2f(-1.0F, -1.0F);
                    GLStateManager.glVertex2f( 1.0F, -1.0F);
                    GLStateManager.glVertex2f( 1.0F,  1.0F);
                    GLStateManager.glVertex2f(-1.0F,  1.0F);
                    GLStateManager.glEnd();

                    GLStateManager.glPopMatrix();
                    GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
                    GLStateManager.glPopMatrix();
                    GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                    GLStateManager.glPopAttrib();
                }
            } else {
                // In-Game GUI (Inventory, Chests, Pause Menu, Options):
                // Clear depth buffer so floating GUI quad renders cleanly and transparently over the world!
                GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);

                // Render floating transparent 3D virtual screen quad
                if (screenTex > 0) {
                    com.gtnhvr.gui.VRVirtualScreen.getInstance().render(eyeIndex, screenTex, screenW, screenH);
                }

                // Render floating 3D virtual keyboard if active
                com.gtnhvr.gui.VRVirtualKeyboard.getInstance().render(mc);

                // Render sleek pointer tool and 3D laser beam
                com.gtnhvr.gui.VRHandRenderer.render(eyeIndex, hmdPos, hmdRot);
                VRControllerHandler.getInstance().renderLaser();
            }
            com.gtnhvr.api.VRAddonRegistry.dispatchWorldRender(mc, eyeIndex, partialTicks, inGameGui);
        } catch (Throwable t) {
            GTNHVRLog.warn("Error rendering stereo overlays for eye %d: %s", eyeIndex, t);
        } finally {
            GLStateManager.glPopMatrix();
            GLStateManager.glPopAttrib();
        }
    }

    private static java.lang.reflect.Field mcFramebufferField = null;
    private static boolean mcFramebufferFieldSearched = false;

    public static void setMinecraftFramebuffer(Minecraft mc, net.minecraft.client.shader.Framebuffer fb) {
        if (!mcFramebufferFieldSearched) {
            mcFramebufferFieldSearched = true;
            try {
                for (java.lang.reflect.Field f : Minecraft.class.getDeclaredFields()) {
                    if (net.minecraft.client.shader.Framebuffer.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        mcFramebufferField = f;
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (mcFramebufferField != null && mc != null) {
            try {
                mcFramebufferField.set(mc, fb);
            } catch (Throwable ignored) {}
        }
    }

    public static net.minecraft.client.shader.Framebuffer getMinecraftFramebuffer(Minecraft mc) {
        if (!mcFramebufferFieldSearched) {
            mcFramebufferFieldSearched = true;
            try {
                for (java.lang.reflect.Field f : Minecraft.class.getDeclaredFields()) {
                    if (net.minecraft.client.shader.Framebuffer.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        mcFramebufferField = f;
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (mcFramebufferField != null && mc != null) {
            try {
                return (net.minecraft.client.shader.Framebuffer) mcFramebufferField.get(mc);
            } catch (Throwable ignored) {}
        }
        return (mc != null) ? mc.getFramebuffer() : null;
    }

    private static java.lang.reflect.Field celeritasInstanceField = null;
    private static java.lang.reflect.Field celeritasMainPassMatricesField = null;
    private static boolean celeritasFieldSearched = false;

    private static void resetCeleritasEyeMatrices() {
        if (!celeritasFieldSearched) {
            celeritasFieldSearched = true;
            try {
                Class<?> clazz = Class.forName("com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer");
                celeritasInstanceField = clazz.getDeclaredField("instance");
                celeritasInstanceField.setAccessible(true);
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (f.getType().getName().contains("ChunkRenderMatrices")) {
                        f.setAccessible(true);
                        celeritasMainPassMatricesField = f;
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (celeritasMainPassMatricesField != null && celeritasInstanceField != null) {
            try {
                Object wr = celeritasInstanceField.get(null);
                if (wr != null) {
                    celeritasMainPassMatricesField.set(wr, null);
                }
            } catch (Throwable ignored) {}
        }
    }

    public static FloatBuffer getEyeProjectionBuffer(int eyeIndex, float nearPlane, float farPlane) {
        if (views == null || eyeIndex < 0 || eyeIndex >= views.capacity()) return null;

        XrFovf fov = views.get(eyeIndex).fov();
        Matrix4f mat = new Matrix4f().setPerspectiveOffCenterFov(
                fov.angleLeft(),
                fov.angleRight(),
                fov.angleDown(),
                fov.angleUp(),
                nearPlane,
                farPlane
        );

        projectionBuffer.clear();
        mat.get(projectionBuffer);
        projectionBuffer.rewind();
        return projectionBuffer;
    }

    /** World scale exactly as getEyeModelViewBuffer applies it. */
    private static float computeWorldScale() {
        float scale = com.gtnhvr.settings.VRClientSettings.worldScale > 0.05f ? com.gtnhvr.settings.VRClientSettings.worldScale : 1.0f;
        if (!com.gtnhvr.settings.VRClientSettings.seated
                && com.gtnhvr.settings.VRClientSettings.heightMode != com.gtnhvr.settings.HeightMode.REAL_SIZE
                && com.gtnhvr.settings.VRClientSettings.heightAdjustment == com.gtnhvr.settings.HeightAdjustment.WORLD_SCALE) {
            float standingEye = VRHeightCalibrator.getStandingEyeHeight();
            scale *= Math.max(0.5F, Math.min(2.0F, 1.62F / standingEye));
        }
        return scale;
    }

    private static XrPosef getViewPoseOrHead(int eyeIndex) {
        if (views != null && eyeIndex >= 0 && eyeIndex < views.capacity()) {
            return views.get(eyeIndex).pose();
        }
        return headPose;
    }

    /**
     * Maps a room-space point to world coordinates relative to the render origin (the player's
     * interpolated position), such that the point appears at exactly the same place under
     * getEyeModelViewBuffer as it does under getMenuModelViewBuffer. Hands are drawn with the menu
     * matrix, the world and the crosshair with the eye matrix; using this mapping for the ray start,
     * the picking origin and the crosshair keeps all of them on the controller.
     *
     * Derivation: menu = R^-1 T(-p); eye = R^-1 T(-p') RotY(theta) T(0,-0.12,0) with p' = (px*s, r*s, pz*s)
     * and theta = 180 + baseYaw. Equating both for the same eye-space point gives
     * q = RotY(-theta) (x - p + p') + (0, 0.12, 0).
     */
    public static Vector3f roomToCameraRelative(int eyeIndex, Vector3f room) {
        XrPosef pose = getViewPoseOrHead(eyeIndex);
        if (pose == null) {
            return new Vector3f(room);
        }
        float px = pose.position$().x();
        float py = pose.position$().y();
        float pz = pose.position$().z();
        float scale = computeWorldScale();
        float r = getRoomCameraYOffset(py);

        float sx = room.x - px + px * scale;
        float sy = room.y - py + r * scale;
        float sz = room.z - pz + pz * scale;

        double phi = -Math.toRadians(180.0F + HeadPoseController.getBaseYaw());
        float c = (float) Math.cos(phi);
        float s = (float) Math.sin(phi);
        return new Vector3f(sx * c + sz * s, sy + 0.12F, -sx * s + sz * c);
    }

    /** Inverse of {@link #roomToCameraRelative}. */
    public static Vector3f cameraRelativeToRoom(int eyeIndex, Vector3f rel) {
        XrPosef pose = getViewPoseOrHead(eyeIndex);
        if (pose == null) {
            return new Vector3f(rel);
        }
        float px = pose.position$().x();
        float py = pose.position$().y();
        float pz = pose.position$().z();
        float scale = computeWorldScale();
        float r = getRoomCameraYOffset(py);

        float tx = rel.x;
        float ty = rel.y - 0.12F;
        float tz = rel.z;
        double theta = Math.toRadians(180.0F + HeadPoseController.getBaseYaw());
        float c = (float) Math.cos(theta);
        float s = (float) Math.sin(theta);
        float vx = tx * c + tz * s;
        float vz = -tx * s + tz * c;
        return new Vector3f(vx + px - px * scale, ty + py - r * scale, vz + pz - pz * scale);
    }

    public static FloatBuffer getEyeModelViewBuffer(int eyeIndex, float partialTicks) {
        if (views == null || eyeIndex < 0 || eyeIndex >= views.capacity()) return null;

        XrView view = views.get(eyeIndex);
        XrPosef pose = view.pose();

        float qx = pose.orientation().x();
        float qy = pose.orientation().y();
        float qz = pose.orientation().z();
        float qw = pose.orientation().w();

        float px = pose.position$().x();
        float py = pose.position$().y();
        float pz = pose.position$().z();

        float scale = com.gtnhvr.settings.VRClientSettings.worldScale > 0.05f ? com.gtnhvr.settings.VRClientSettings.worldScale : 1.0f;
        float baseYaw = HeadPoseController.getBaseYaw();

        Matrix4f mat = new Matrix4f();
        // 1. Headset orientation inverse (world-to-camera rotation)
        mat.rotate(new Quaternionf(qx, qy, qz, qw).conjugate());
        float roomYOffset = getRoomCameraYOffset(py);
        if (!com.gtnhvr.settings.VRClientSettings.seated
                && com.gtnhvr.settings.VRClientSettings.heightMode != com.gtnhvr.settings.HeightMode.REAL_SIZE
                && com.gtnhvr.settings.VRClientSettings.heightAdjustment == com.gtnhvr.settings.HeightAdjustment.WORLD_SCALE) {
            float standingEye = VRHeightCalibrator.getStandingEyeHeight();
            scale *= Math.max(0.5F, Math.min(2.0F, 1.62F / standingEye));
        }
        mat.translate(-px * scale, -roomYOffset * scale, -pz * scale);
        // 3. Align Minecraft world coordinates with physical room (180 + baseYaw)
        mat.rotateY((float) Math.toRadians(180.0F + baseYaw));
        // 4. Vanilla / Celeritas eye height compensation (lowered by 0.5 blocks to match hand models and pointers)
        mat.translate(0.0F, -0.12F, 0.0F);

        modelViewBuffer.clear();
        mat.get(modelViewBuffer);
        modelViewBuffer.rewind();
        return modelViewBuffer;
    }

    public static float getRoomCameraYOffset(float py) {
        float effectivePy = py;
        if (VRControllerHandler.getInstance().isSneakActive()) {
            effectivePy -= 0.28F;
        }
        if (com.gtnhvr.settings.VRClientSettings.seated) {
            if (Float.isNaN(seatedBaselineY)) seatedBaselineY = effectivePy;
            return isStageSpace ? (effectivePy - seatedBaselineY) : effectivePy;
        } else {
            float standingEye = VRHeightCalibrator.getStandingEyeHeight();
            if (com.gtnhvr.settings.VRClientSettings.heightMode == com.gtnhvr.settings.HeightMode.REAL_SIZE) {
                return isStageSpace ? (effectivePy - 1.62F) : (effectivePy + (standingEye - 1.62F));
            } else {
                return isStageSpace ? (effectivePy - standingEye) : effectivePy;
            }
        }
    }

    public static float getRoomCameraYOffset() {
        float py = (headPose != null) ? headPose.position$().y() : 1.62F;
        return getRoomCameraYOffset(py);
    }

    public static FloatBuffer getMenuModelViewBuffer(int eyeIndex) {
        if (views == null || eyeIndex < 0 || eyeIndex >= views.capacity()) return null;

        XrView view = views.get(eyeIndex);
        XrPosef pose = view.pose();

        float qx = pose.orientation().x();
        float qy = pose.orientation().y();
        float qz = pose.orientation().z();
        float qw = pose.orientation().w();

        float px = pose.position$().x();
        float py = pose.position$().y();
        float pz = pose.position$().z();

        Matrix4f mat = new Matrix4f();
        mat.rotate(new Quaternionf(qx, qy, qz, qw).conjugate());
        mat.translate(-px, -py, -pz);

        menuModelViewBuffer.clear();
        mat.get(menuModelViewBuffer);
        menuModelViewBuffer.rewind();
        return menuModelViewBuffer;
    }

    public static void finishFrame() {
        if (com.gtnhvr.settings.VRClientSettings.vrPlayMode == com.gtnhvr.settings.VRPlayMode.DISABLED
                || session == null || !sessionRunning || !frameBegun) return;

        try (MemoryStack stack = stackPush()) {
            if (!frameState.shouldRender()) {
                xrEndFrameWithoutLayer();
                return;
            }

            XrCompositionLayerProjectionView.Buffer projectionViews =
                    XrCompositionLayerProjectionView.calloc(viewCount, stack);
            for (int i = 0; i < viewCount; i++) {
                projectionViews.get(i).type(XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW)
                        .pose(views.get(i).pose())
                        .fov(views.get(i).fov());
                XrRect2Di rect = projectionViews.get(i).subImage().imageRect();
                rect.offset().set(0, 0);
                rect.extent().set(eyes[i].width, eyes[i].height);
                projectionViews.get(i).subImage().swapchain(eyes[i].swapchain).imageArrayIndex(0);
            }

            XrCompositionLayerProjection layer = XrCompositionLayerProjection.calloc(stack)
                    .type(XR_TYPE_COMPOSITION_LAYER_PROJECTION)
                    .space(localSpace)
                    .views(projectionViews);
            PointerBuffer layers = stack.mallocPointer(1).put(layer.address()).flip();

            XrFrameEndInfo end = XrFrameEndInfo.calloc(stack)
                    .type(XR_TYPE_FRAME_END_INFO)
                    .displayTime(frameState.predictedDisplayTime())
                    .environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE)
                    .layers(layers);
            end.layerCount(1);
            check(xrEndFrame(session, end), "xrEndFrame");
        } catch (Throwable t) {
            GTNHVRLog.warn("OpenXR finishFrame error: %s", t);
            try { xrEndFrameWithoutLayer(); } catch (Throwable ignored) {}
        } finally {
            frameBegun = false;
        }
    }

    public static void endFrame() {
        if (!frameBegun) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            finishFrame();
            return;
        }

        int guiTex = com.gtnhvr.gui.VRGuiRenderer.getTextureId();
        int desktopTexture = (guiTex > 0) ? guiTex : ((mc.getFramebuffer() != null && mc.getFramebuffer().framebufferTexture > 0) ? mc.getFramebuffer().framebufferTexture : 0);
        int screenW = (guiTex > 0) ? com.gtnhvr.gui.VRGuiRenderer.getWidth() : (mc.displayWidth > 0 ? mc.displayWidth : 854);
        int screenH = (guiTex > 0) ? com.gtnhvr.gui.VRGuiRenderer.getHeight() : (mc.displayHeight > 0 ? mc.displayHeight : 480);

        int desktopFbo = (mc.getFramebuffer() != null && mc.getFramebuffer().framebufferObject > 0)
                ? mc.getFramebuffer().framebufferObject : 0;
        int desktopW = (mc.getFramebuffer() != null && mc.getFramebuffer().framebufferWidth > 0)
                ? mc.getFramebuffer().framebufferWidth : (mc.displayWidth > 0 ? mc.displayWidth : 854);
        int desktopH = (mc.getFramebuffer() != null && mc.getFramebuffer().framebufferHeight > 0)
                ? mc.getFramebuffer().framebufferHeight : (mc.displayHeight > 0 ? mc.displayHeight : 480);

        if (eyes != null && eyes.length >= 2 && frameState != null && frameState.shouldRender()) {
            Vector3f hmdPos = new Vector3f(headPose.position$().x(), headPose.position$().y(), headPose.position$().z());
            Quaternionf hmdRot = new Quaternionf(headPose.orientation().x(), headPose.orientation().y(), headPose.orientation().z(), headPose.orientation().w());

            if (mc.theWorld == null) {
                // ---- MAIN MENU PASS (3D GTNH platform, rotating panorama, virtual screen) ----
                HeadPoseController.setBaseYaw(0.0F);
                HeadPoseController.resetCalibration();
                com.gtnhvr.gui.VRVirtualScreen.getInstance().updateScreenPlacement(true, hmdPos, hmdRot);
                com.gtnhvr.gui.VRMenuEnvironment.getInstance().update();

                for (int eyeIndex = 0; eyeIndex < 2; eyeIndex++) {
                    EyeSwapchain eye = eyes[eyeIndex];
                    eye.beginRender();
                    try {
                        GLStateManager.glViewport(0, 0, eye.width, eye.height);

                        // Clear background to deep-space void
                        GLStateManager.glClearColor(0.015F, 0.025F, 0.05F, 1.0F);
                        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

                        // Set 3D eye projection & modelview for virtual screen and environment
                        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
                        GLStateManager.glLoadMatrix(getEyeProjectionBuffer(eyeIndex, 0.05F, 100.0F));

                        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                        FloatBuffer mv = getMenuModelViewBuffer(eyeIndex);
                        if (mv != null) {
                            GLStateManager.glLoadMatrix(mv);
                        }

                        // Render authentic GTNH 3D Main Menu environment (platform & panorama)
                        com.gtnhvr.gui.VRMenuEnvironment.getInstance().render();

                        // Render floating 3D virtual screen quad
                        com.gtnhvr.gui.VRVirtualScreen.getInstance().render(eyeIndex, desktopTexture, screenW, screenH);

                        // Render floating 3D virtual keyboard if active
                        com.gtnhvr.gui.VRVirtualKeyboard.getInstance().render(mc);

                        // Render sleek pointer tool at hand and 3D laser beam pointing at virtual screen
                        com.gtnhvr.gui.VRHandRenderer.render(eyeIndex, hmdPos, hmdRot);
                        VRControllerHandler.getInstance().renderLaser();
                        // The main menu has no in-world interpolation; pass a stable zero partial tick on this path.
                        com.gtnhvr.api.VRAddonRegistry.dispatchWorldRender(mc, eyeIndex, 0.0F, true);

                        if (eyeIndex == (com.gtnhvr.settings.VRClientSettings.mirrorEye == 1 ? 1 : 0)) {
                            blitToDesktop(eye, desktopFbo, desktopW, desktopH);
                        }
                    } finally {
                        eye.endRender();
                    }
                }
            }
        }

        finishFrame();
    }

    private static void blitToDesktop(EyeSwapchain eye, int targetFbo, int targetW, int targetH) {
        if (eye == null) return;
        if (com.gtnhvr.settings.VRClientSettings.mirrorMode == com.gtnhvr.settings.MirrorMode.OFF) return;

        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, eye.fbo);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFbo);

        if (com.gtnhvr.settings.VRClientSettings.mirrorMode == com.gtnhvr.settings.MirrorMode.CROPPED && targetW > 0 && targetH > 0) {
            float targetAspect = (float) targetW / (float) targetH;
            float srcAspect = (float) eye.width / (float) eye.height;
            int srcX0 = 0, srcY0 = 0, srcX1 = eye.width, srcY1 = eye.height;
            if (srcAspect > targetAspect) {
                int cropW = (int) (eye.height * targetAspect);
                srcX0 = (eye.width - cropW) / 2;
                srcX1 = srcX0 + cropW;
            } else {
                int cropH = (int) (eye.width / targetAspect);
                srcY0 = (eye.height - cropH) / 2;
                srcY1 = srcY0 + cropH;
            }
            GLStateManager.glBlitFramebuffer(
                    srcX0, srcY0, srcX1, srcY1,
                    0, 0, targetW, targetH,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_LINEAR
            );
        } else {
            GLStateManager.glBlitFramebuffer(
                    0, 0, eye.width, eye.height,
                    0, 0, targetW, targetH,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_LINEAR
            );
        }

        // Restore single framebuffer target binding
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);
    }

    private static void xrEndFrameWithoutLayer() {
        if (!frameBegun || session == null) return;
        try (MemoryStack stack = stackPush()) {
            XrFrameEndInfo end = XrFrameEndInfo.calloc(stack)
                    .type(XR_TYPE_FRAME_END_INFO)
                    .displayTime(frameState.predictedDisplayTime())
                    .environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE);
            xrEndFrame(session, end);
        }
    }

    public static void pollEvents() {
        if (instance == null) return;
        try (MemoryStack stack = stackPush()) {
            XrEventDataBuffer buffer = XrEventDataBuffer.calloc(stack).type(XR_TYPE_EVENT_DATA_BUFFER);
            while (xrPollEvent(instance, buffer) == XR_SUCCESS) {
                int type = buffer.type();
                if (type == XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
                    XrEventDataSessionStateChanged event = XrEventDataSessionStateChanged.create(buffer.address());
                    int state = event.state();
                    GTNHVRLog.info("OpenXR session state: %s", sessionStateName(state));
                    if (state == XR_SESSION_STATE_READY) {
                        XrSessionBeginInfo begin = XrSessionBeginInfo.calloc(stack)
                                .type(XR_TYPE_SESSION_BEGIN_INFO)
                                .primaryViewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);
                        check(xrBeginSession(session, begin), "xrBeginSession");
                        sessionRunning = true;
                        sessionBegun = true;
                        try {
                            long win = org.lwjgl.sdl.SDLVideo.SDL_GL_GetCurrentWindow();
                            if (win != 0) org.lwjgl.sdl.SDLVideo.SDL_RaiseWindow(win);
                        } catch (Throwable ignored) {}
                    } else if (state == XR_SESSION_STATE_VISIBLE || state == XR_SESSION_STATE_FOCUSED) {
                        try {
                            long win = org.lwjgl.sdl.SDLVideo.SDL_GL_GetCurrentWindow();
                            if (win != 0) org.lwjgl.sdl.SDLVideo.SDL_RaiseWindow(win);
                        } catch (Throwable ignored) {}
                    } else if (state == XR_SESSION_STATE_STOPPING) {
                        xrEndSession(session);
                        sessionRunning = false;
                        sessionBegun = false;
                    } else if (state == XR_SESSION_STATE_EXITING || state == XR_SESSION_STATE_LOSS_PENDING) {
                        sessionRunning = false;
                        sessionBegun = false;
                    }
                }
                buffer.type(XR_TYPE_EVENT_DATA_BUFFER);
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("Error polling OpenXR events: %s", t);
        }
    }

    private static String sessionStateName(int s) {
        switch (s) {
            case XR_SESSION_STATE_IDLE: return "IDLE";
            case XR_SESSION_STATE_READY: return "READY";
            case XR_SESSION_STATE_SYNCHRONIZED: return "SYNCHRONIZED";
            case XR_SESSION_STATE_VISIBLE: return "VISIBLE";
            case XR_SESSION_STATE_FOCUSED: return "FOCUSED";
            case XR_SESSION_STATE_STOPPING: return "STOPPING";
            case XR_SESSION_STATE_EXITING: return "EXITING";
            case XR_SESSION_STATE_LOSS_PENDING: return "LOSS_PENDING";
            default: return Integer.toString(s);
        }
    }

    public static boolean isReady() { return session != null; }
    public static boolean isSessionRunning() { return sessionRunning; }
    public static boolean isVRStereoActive() { return vrStereoActive; }
    public static boolean isRenderingEyePass() { return renderingEyePass; }
    public static int getCurrentEyeIndex() { return currentEyeIndex; }
    public static XrPosef getHeadPose() { return headPose; }

    public static net.minecraft.client.shader.Framebuffer getCurrentEyeFramebuffer() {
        if (vrStereoActive && eyeFramebuffers != null && currentEyeIndex >= 0 && currentEyeIndex < eyeFramebuffers.length) {
            return eyeFramebuffers[currentEyeIndex];
        }
        return null;
    }

    public static int getEyeWidth(int eyeIndex) {
        return (eyes != null && eyeIndex >= 0 && eyeIndex < eyes.length && eyes[eyeIndex] != null) ? eyes[eyeIndex].width : 2064;
    }

    public static int getEyeHeight(int eyeIndex) {
        return (eyes != null && eyeIndex >= 0 && eyeIndex < eyes.length && eyes[eyeIndex] != null) ? eyes[eyeIndex].height : 2208;
    }

    public static synchronized void shutdown() {
        try { if (frameBegun) xrEndFrameWithoutLayer(); } catch (Throwable ignored) {}
        try { if (session != null && sessionBegun) xrRequestExitSession(session); } catch (Throwable ignored) {}
        try { com.gtnhvr.VRControllerHandler.getInstance().destroy(); } catch (Throwable ignored) {}
        try { if (localSpace != null) { xrDestroySpace(localSpace); localSpace = null; } } catch (Throwable ignored) {}
        try { if (eyes != null) { for (EyeSwapchain eye : eyes) eye.destroy(); eyes = null; } } catch (Throwable ignored) {}
        try { if (session != null) { xrDestroySession(session); session = null; } } catch (Throwable ignored) {}
        try { com.gtnhvr.gui.VRGuiRenderer.destroy(); } catch (Throwable ignored) {}
        try { com.gtnhvr.gui.VRVirtualKeyboard.getInstance().destroy(); } catch (Throwable ignored) {}
        try { if (instance != null) { xrDestroyInstance(instance); instance = null; } } catch (Throwable ignored) {}
        session = null; localSpace = null; instance = null; eyes = null; eyeFramebuffers = null;
        initialized = false; sessionRunning = false; sessionBegun = false; frameBegun = false;
    }

    private static void check(int result, String op) {
        if (result != XR_SUCCESS) throw new IllegalStateException(op + " failed: " + result);
    }

    public static final class EyeSwapchain {
        public final XrSwapchain swapchain;
        public final int width, height;
        public final XrSwapchainImageOpenGLKHR.Buffer images;
        public final int fbo;
        public final int depthTex;
        public final int eyeIndex;
        private int currentImageIndex;

        private EyeSwapchain(XrSwapchain swapchain, int width, int height,
                             XrSwapchainImageOpenGLKHR.Buffer images, int fbo, int depthTex, int eyeIndex) {
            this.swapchain = swapchain;
            this.width = width;
            this.height = height;
            this.images = images;
            this.fbo = fbo;
            this.depthTex = depthTex;
            this.eyeIndex = eyeIndex;
        }

        static EyeSwapchain create(XrSession session, long format, int width, int height, int samples, MemoryStack stack, int eyeIndex) {
            XrSwapchainCreateInfo ci = XrSwapchainCreateInfo.calloc(stack)
                    .type(XR_TYPE_SWAPCHAIN_CREATE_INFO)
                    .usageFlags(XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT)
                    .format(format).sampleCount(samples).width(width).height(height)
                    .faceCount(1).arraySize(1).mipCount(1);
            PointerBuffer out = stack.mallocPointer(1);
            check(xrCreateSwapchain(session, ci, out), "xrCreateSwapchain");
            XrSwapchain sc = new XrSwapchain(out.get(0), session);
            IntBuffer count = stack.ints(0);
            check(xrEnumerateSwapchainImages(sc, count, null), "xrEnumerateSwapchainImages(count)");
            XrSwapchainImageOpenGLKHR.Buffer imgs = XrSwapchainImageOpenGLKHR.calloc(count.get(0));
            for (int i = 0; i < imgs.capacity(); i++) imgs.get(i).type(XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR);
            check(xrEnumerateSwapchainImages(sc, count, XrSwapchainImageBaseHeader.create(imgs.address(), imgs.capacity())), "xrEnumerateSwapchainImages(data)");

            int fbo = GLStateManager.glGenFramebuffers();
            int depthTex = com.gtnewhorizons.angelica.glsm.GLStateManager.glGenTextures();
            com.gtnewhorizons.angelica.glsm.GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
            com.gtnewhorizons.angelica.glsm.GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            com.gtnewhorizons.angelica.glsm.GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            com.gtnewhorizons.angelica.glsm.GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            com.gtnewhorizons.angelica.glsm.GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            com.gtnewhorizons.angelica.glsm.GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE, 0);

            boolean stencil = net.minecraftforge.client.MinecraftForgeClient.getStencilBits() != 0;
            if (stencil) {
                com.gtnewhorizons.angelica.glsm.GLStateManager.glTexImage2D(
                        GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8, width, height, 0,
                        GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, (IntBuffer) null
                );
            } else {
                com.gtnewhorizons.angelica.glsm.GLStateManager.glTexImage2D(
                        GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0,
                        GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (IntBuffer) null
                );
            }

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GLStateManager.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTex, 0
            );
            if (stencil) {
                GLStateManager.glFramebufferTexture2D(
                        GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTex, 0
                );
            }
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

            return new EyeSwapchain(sc, width, height, imgs, fbo, depthTex, eyeIndex);
        }

        public void beginRender() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                XrSwapchainImageAcquireInfo acquire = XrSwapchainImageAcquireInfo.calloc(stack)
                        .type(XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO);
                IntBuffer idx = stack.ints(0);
                check(xrAcquireSwapchainImage(swapchain, acquire, idx), "xrAcquireSwapchainImage");
                currentImageIndex = idx.get(0);

                XrSwapchainImageWaitInfo wait = XrSwapchainImageWaitInfo.calloc(stack)
                        .type(XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO)
                        .timeout(XR_INFINITE_DURATION);
                check(xrWaitSwapchainImage(swapchain, wait), "xrWaitSwapchainImage");

                int textureId = images.get(currentImageIndex).image();

                if (eyeFramebuffers != null && eyeIndex >= 0 && eyeIndex < eyeFramebuffers.length && eyeFramebuffers[eyeIndex] != null) {
                    eyeFramebuffers[eyeIndex].updateFromSwapchain(fbo, textureId, 0, depthTex, width, height);
                }

                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textureId, 0);
                GLStateManager.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                GLStateManager.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

                int status = GLStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    GTNHVRLog.throttled("Eye swapchain FBO incomplete: 0x%X", status);
                }
            }
        }

        public void endRender() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                XrSwapchainImageReleaseInfo release = XrSwapchainImageReleaseInfo.calloc(stack)
                        .type(XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO);
                check(xrReleaseSwapchainImage(swapchain, release), "xrReleaseSwapchainImage");
            }
        }

        void copyCurrentFramebuffer(MemoryStack stack) {
            beginRender();
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null) {
                    int srcFbo = (mc.getFramebuffer() != null && mc.getFramebuffer().framebufferObject > 0)
                            ? mc.getFramebuffer().framebufferObject : 0;
                    int srcW = (mc.getFramebuffer() != null && mc.getFramebuffer().framebufferWidth > 0)
                            ? mc.getFramebuffer().framebufferWidth : (mc.displayWidth > 0 ? mc.displayWidth : 854);
                    int srcH = (mc.getFramebuffer() != null && mc.getFramebuffer().framebufferHeight > 0)
                            ? mc.getFramebuffer().framebufferHeight : (mc.displayHeight > 0 ? mc.displayHeight : 480);

                    GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
                    GLStateManager.glReadBuffer(srcFbo == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);

                    GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
                    GLStateManager.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);

                    GLStateManager.glBlitFramebuffer(
                            0, 0, srcW, srcH,
                            0, 0, width, height,
                            GL11.GL_COLOR_BUFFER_BIT,
                            GL11.GL_LINEAR
                    );
                }
            } finally {
                endRender();
            }
        }

        void destroy() {
            try { xrDestroySwapchain(swapchain); } catch (Throwable ignored) {}
            try { images.free(); } catch (Throwable ignored) {}
            if (fbo != 0) {
                try { GLStateManager.glDeleteFramebuffers(fbo); } catch (Throwable ignored) {}
            }
            if (depthTex != 0) {
                try { com.gtnewhorizons.angelica.glsm.GLStateManager.glDeleteTextures(depthTex); } catch (Throwable ignored) {}
            }
        }
    }
}
