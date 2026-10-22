package com.gtnhvr.api;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.GTNHVRLog;
import com.gtnhvr.VRControllerHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client extension points for GTNHVR add-ons.
 *
 * Register listeners from an add-on's client proxy after GTNHVR has initialized.
 * Listener failures are isolated so a broken third-party extension cannot stop the VR frame.
 * This API targets Forge/Minecraft 1.7.10 instead of exposing incompatible modern Visor classes.
 *
 * This class is client-only; do not reference it from common code that can load on a dedicated server.
 */
@SideOnly(Side.CLIENT)
public final class VRAddonRegistry {
    private static final Map<String, List<ClientTickListener>> TICK_LISTENERS = new LinkedHashMap<>();
    private static final Map<String, List<WorldRenderListener>> WORLD_RENDER_LISTENERS = new LinkedHashMap<>();
    private static final Map<String, List<HandItemPoseProvider>> HAND_POSE_PROVIDERS = new LinkedHashMap<>();
    private static final Map<String, List<LocalPoseListener>> LOCAL_POSE_LISTENERS = new LinkedHashMap<>();

    private VRAddonRegistry() {}

    /** Client gameplay tick, dispatched only while the OpenXR session is active. */
    public interface ClientTickListener {
        void onClientTick(Minecraft minecraft, VRControllerHandler controllers);
    }

    /** Called once per eye after GTNHVR's world or GUI overlays have been drawn. */
    public interface WorldRenderListener {
        void onWorldRender(Minecraft minecraft, int eyeIndex, float partialTicks, boolean guiOpen);
    }

    /** Receives the latest local OpenXR HMD and controller/grip poses in tracking-space coordinates. */
    public interface LocalPoseListener {
        void onLocalPose(Minecraft minecraft, PoseSnapshot pose);
    }

    /** Immutable scalar snapshot of one tracked device pose. Position is in metres, rotation is a quaternion. */
    public static final class DevicePose {
        public final boolean tracked;
        public final boolean orientationTracked;
        public final float x, y, z;
        public final float qx, qy, qz, qw;

        private DevicePose(boolean tracked, boolean orientationTracked,
                           float x, float y, float z,
                           float qx, float qy, float qz, float qw) {
            this.tracked = tracked;
            this.orientationTracked = orientationTracked;
            this.x = x;
            this.y = y;
            this.z = z;
            this.qx = qx;
            this.qy = qy;
            this.qz = qz;
            this.qw = qw;
        }

        private static DevicePose untracked() {
            return new DevicePose(false, false, 0, 0, 0, 0, 0, 0, 1);
        }

        private static DevicePose of(boolean tracked, boolean orientationTracked,
                                     org.joml.Vector3f position, org.joml.Quaternionf rotation) {
            if (position == null || rotation == null) return untracked();
            return new DevicePose(tracked, orientationTracked,
                    position.x, position.y, position.z,
                    rotation.x, rotation.y, rotation.z, rotation.w);
        }

        private static DevicePose of(org.lwjgl.openxr.XrPosef pose) {
            if (pose == null) return untracked();
            return new DevicePose(true, true,
                    pose.position$().x(), pose.position$().y(), pose.position$().z(),
                    pose.orientation().x(), pose.orientation().y(),
                    pose.orientation().z(), pose.orientation().w());
        }
    }

    /** One point-in-time snapshot; data is copied from OpenXR and is not backed by mutable controller vectors. */
    public static final class PoseSnapshot {
        public final long capturedAtNanos;
        public final DevicePose head;
        public final DevicePose leftAim;
        public final DevicePose rightAim;
        public final DevicePose leftGrip;
        public final DevicePose rightGrip;

        private PoseSnapshot(long capturedAtNanos, DevicePose head,
                             DevicePose leftAim, DevicePose rightAim,
                             DevicePose leftGrip, DevicePose rightGrip) {
            this.capturedAtNanos = capturedAtNanos;
            this.head = head;
            this.leftAim = leftAim;
            this.rightAim = rightAim;
            this.leftGrip = leftGrip;
            this.rightGrip = rightGrip;
        }
    }

    /**
     * Supplies an item transform in controller-local space. Returning false allows the next
     * provider and then GTNHVR's built-in category transform to be used.
     */
    public interface HandItemPoseProvider {
        boolean getPose(Minecraft minecraft, EntityPlayer player, ItemStack stack, boolean rightHand,
                        VRControllerHandler controllers, HandItemTransform outPose);
    }

    /** Translation in metres, Euler rotations in degrees, and per-axis scale. */
    public static final class HandItemTransform {
        public float translateX;
        public float translateY;
        public float translateZ;
        public float yaw;
        public float pitch;
        public float roll;
        public float scaleX = 1.0F;
        public float scaleY = 1.0F;
        public float scaleZ = 1.0F;

        public HandItemTransform set(float x, float y, float z, float yawDegrees,
                                     float pitchDegrees, float rollDegrees, float scale) {
            return set(x, y, z, yawDegrees, pitchDegrees, rollDegrees, scale, scale, scale);
        }

        public HandItemTransform set(float x, float y, float z, float yawDegrees,
                                     float pitchDegrees, float rollDegrees,
                                     float sx, float sy, float sz) {
            translateX = x;
            translateY = y;
            translateZ = z;
            yaw = yawDegrees;
            pitch = pitchDegrees;
            roll = rollDegrees;
            scaleX = sx;
            scaleY = sy;
            scaleZ = sz;
            return this;
        }

        private boolean isFinite() {
            return finite(translateX) && finite(translateY) && finite(translateZ)
                    && finite(yaw) && finite(pitch) && finite(roll)
                    && finite(scaleX) && finite(scaleY) && finite(scaleZ);
        }

        private static boolean finite(float value) {
            return !Float.isNaN(value) && !Float.isInfinite(value);
        }
    }

    private static final class Callback<T> {
        private final String addonId;
        private final T listener;

        private Callback(String addonId, T listener) {
            this.addonId = addonId;
            this.listener = listener;
        }
    }

    public static synchronized void registerClientTickListener(String addonId, ClientTickListener listener) {
        register(TICK_LISTENERS, addonId, listener);
    }

    public static synchronized void registerWorldRenderListener(String addonId, WorldRenderListener listener) {
        register(WORLD_RENDER_LISTENERS, addonId, listener);
    }

    public static synchronized void registerHandItemPoseProvider(String addonId, HandItemPoseProvider provider) {
        register(HAND_POSE_PROVIDERS, addonId, provider);
    }

    public static synchronized void registerLocalPoseListener(String addonId, LocalPoseListener listener) {
        register(LOCAL_POSE_LISTENERS, addonId, listener);
    }

    /** Removes every callback registered by the supplied add-on ID. */
    public static synchronized void unregisterAddon(String addonId) {
        String id = normalizeId(addonId);
        TICK_LISTENERS.remove(id);
        WORLD_RENDER_LISTENERS.remove(id);
        HAND_POSE_PROVIDERS.remove(id);
        LOCAL_POSE_LISTENERS.remove(id);
    }

    private static <T> void register(Map<String, List<T>> registry, String addonId, T listener) {
        String id = normalizeId(addonId);
        if (listener == null) {
            throw new IllegalArgumentException("VR add-on listener cannot be null");
        }
        List<T> listeners = registry.computeIfAbsent(id, ignored -> new ArrayList<>());
        // Avoid accidentally dispatching the same listener twice if an add-on re-registers on reload.
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    private static String normalizeId(String addonId) {
        if (addonId == null || addonId.trim().isEmpty()) {
            throw new IllegalArgumentException("VR add-on ID cannot be blank");
        }
        return addonId.trim();
    }

    private static <T> List<Callback<T>> snapshot(Map<String, List<T>> registry) {
        synchronized (VRAddonRegistry.class) {
            List<Callback<T>> callbacks = new ArrayList<>();
            for (Map.Entry<String, List<T>> entry : registry.entrySet()) {
                for (T listener : entry.getValue()) {
                    callbacks.add(new Callback<>(entry.getKey(), listener));
                }
            }
            return callbacks;
        }
    }

    public static void dispatchClientTick(Minecraft mc) {
        if (mc == null) return;
        VRControllerHandler controllers = VRControllerHandler.getInstance();
        for (Callback<ClientTickListener> callback : snapshot(TICK_LISTENERS)) {
            try {
                callback.listener.onClientTick(mc, controllers);
            } catch (Throwable t) {
                GTNHVRLog.warn("VR add-on %s failed in client tick callback: %s", callback.addonId, t);
            }
        }

        List<Callback<LocalPoseListener>> poseCallbacks = snapshot(LOCAL_POSE_LISTENERS);
        if (!poseCallbacks.isEmpty()) {
            org.lwjgl.openxr.XrPosef headPose = com.gtnhvr.OpenXRRuntime.getHeadPose();
            PoseSnapshot pose = new PoseSnapshot(System.nanoTime(),
                    DevicePose.of(headPose),
                    DevicePose.of(controllers.isLeftHandTracked(), controllers.isLeftHandTracked(),
                            controllers.getLeftHandPos(), controllers.getLeftHandRot()),
                    DevicePose.of(controllers.isRightHandTracked(), controllers.isRightHandTracked(),
                            controllers.getRightHandPos(), controllers.getRightHandRot()),
                    DevicePose.of(controllers.isLeftGripHandTracked(), controllers.isLeftGripHandOrientationTracked(),
                            controllers.getLeftGripHandPos(), controllers.getLeftGripHandRot()),
                    DevicePose.of(controllers.isRightGripHandTracked(), controllers.isRightGripHandOrientationTracked(),
                            controllers.getRightGripHandPos(), controllers.getRightGripHandRot()));
            for (Callback<LocalPoseListener> callback : poseCallbacks) {
                try {
                    callback.listener.onLocalPose(mc, pose);
                } catch (Throwable t) {
                    GTNHVRLog.warn("VR add-on %s failed in local pose callback: %s", callback.addonId, t);
                }
            }
        }
    }

    public static void dispatchWorldRender(Minecraft mc, int eyeIndex, float partialTicks, boolean guiOpen) {
        if (mc == null) return;
        for (Callback<WorldRenderListener> callback : snapshot(WORLD_RENDER_LISTENERS)) {
            int previousFramebuffer = GLStateManager.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int previousProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int previousActiveTexture = GLStateManager.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int previousMatrixMode = GLStateManager.glGetInteger(GL11.GL_MATRIX_MODE);
            GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            // Push both matrix stacks explicitly: callbacks may legitimately switch between
            // projection and model-view while drawing a custom overlay.
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPushMatrix();
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPushMatrix();
            try {
                callback.listener.onWorldRender(mc, eyeIndex, partialTicks, guiOpen);
            } catch (Throwable t) {
                GTNHVRLog.warn("VR add-on %s failed in world render callback: %s", callback.addonId, t);
            } finally {
                GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                GLStateManager.glPopMatrix();
                GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
                GLStateManager.glPopMatrix();
                GLStateManager.glPopAttrib();
                GLStateManager.glActiveTexture(previousActiveTexture);
                GLStateManager.glUseProgram(previousProgram);
                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
                GLStateManager.glMatrixMode(previousMatrixMode);
            }
        }
    }

    /** Returns the first finite custom pose supplied by a registered provider, or null. */
    public static HandItemTransform findHandItemPose(Minecraft mc, EntityPlayer player, ItemStack stack,
                                                      boolean rightHand) {
        if (mc == null || player == null || stack == null) return null;
        VRControllerHandler controllers = VRControllerHandler.getInstance();
        for (Callback<HandItemPoseProvider> callback : snapshot(HAND_POSE_PROVIDERS)) {
            HandItemTransform candidate = new HandItemTransform();
            try {
                if (callback.listener.getPose(mc, player, stack, rightHand, controllers, candidate)) {
                    if (candidate.isFinite()) return candidate;
                    GTNHVRLog.warn("VR add-on %s returned a non-finite hand item pose; ignoring it", callback.addonId);
                }
            } catch (Throwable t) {
                GTNHVRLog.warn("VR add-on %s failed in hand item pose callback: %s", callback.addonId, t);
            }
        }
        return null;
    }
}
