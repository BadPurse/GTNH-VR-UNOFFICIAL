package com.gtnhvr.gameplay;

import com.gtnhvr.GTNHVRLog;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrPosef;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Two configurable virtual controller buttons triggered by opposite-shoulder gestures.
 *
 * Right physical controller at the left shoulder emits the selected keyboard key.
 * Left physical controller at the right shoulder emits its independently selected key.
 *
 * No jetpack mods or named mod-specific actions are assumed. Any keyboard key can be
 * selected in the controls screen; Minecraft/Forge/mod key bindings assigned to that
 * key receive the synthetic press.
 */
@Lwjgl3Aware
public final class VRShoulderGestures {
    private static final VRShoulderGestures INSTANCE = new VRShoulderGestures();

    private static final float SHOULDER_OFFSET_Y = 0.10F;
    private static final float SHOULDER_OFFSET_X = 0.18F;
    private static final long COOLDOWN_MS = 250L;
    private static final long SYNTHETIC_KEY_PRESS_NANOS = 120_000_000L;

    private long rightHandOnLeftShoulderStart;
    private boolean rightHandWasOnShoulder;
    private boolean rightHandGestureFired;
    private long lastRightGestureTime;

    private long leftHandOnRightShoulderStart;
    private boolean leftHandWasOnShoulder;
    private boolean leftHandGestureFired;
    private long lastLeftGestureTime;

    private final Map<Integer, Long> pendingKeyReleases = new HashMap<>();

    private VRShoulderGestures() {}

    public static VRShoulderGestures getInstance() {
        return INSTANCE;
    }

    private static float getGestureDistance() {
        return Math.max(0.10F, Math.min(0.45F, VRClientSettings.shoulderGestureDistance));
    }

    private static long getGestureHoldDurationMs() {
        float seconds = Math.max(0.25F, Math.min(2.50F, VRClientSettings.shoulderGestureHoldSeconds));
        return Math.round(seconds * 1000.0F);
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (!VRClientSettings.shoulderGesturesEnabled) {
            resetGestureState();
            return;
        }
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            resetGestureState();
            return;
        }

        boolean rightEnabled = VRClientSettings.shoulderRightToLeftEnabled
                && VRClientSettings.shoulderRightToLeftKeyCode > 0;
        boolean leftEnabled = VRClientSettings.shoulderLeftToRightEnabled
                && VRClientSettings.shoulderLeftToRightKeyCode > 0;

        if (!rightEnabled) {
            rightHandWasOnShoulder = false;
            rightHandGestureFired = false;
        }
        if (!leftEnabled) {
            leftHandWasOnShoulder = false;
            leftHandGestureFired = false;
        }
        if (!rightEnabled && !leftEnabled) return;

        XrPosef headPose = OpenXRRuntime.getHeadPose();
        if (headPose == null) {
            resetGestureState();
            return;
        }

        Vector3f headPos = new Vector3f(
                headPose.position$().x(),
                headPose.position$().y(),
                headPose.position$().z()
        );
        Quaternionf headRot = new Quaternionf(
                headPose.orientation().x(),
                headPose.orientation().y(),
                headPose.orientation().z(),
                headPose.orientation().w()
        );

        Vector3f leftShoulder = headRot.transform(
                new Vector3f(-SHOULDER_OFFSET_X, -SHOULDER_OFFSET_Y, 0.0F)
        ).add(headPos);
        Vector3f rightShoulder = headRot.transform(
                new Vector3f(SHOULDER_OFFSET_X, -SHOULDER_OFFSET_Y, 0.0F)
        ).add(headPos);

        long now = System.currentTimeMillis();
        float distanceThreshold = getGestureDistance();
        long holdDurationMs = getGestureHoldDurationMs();

        // Physical right controller -> left shoulder.
        if (rightEnabled && handler.isRightGripHandTracked()) {
            Vector3f rightGrip = handler.getRightGripHandPos();
            float distance = new Vector3f(rightGrip).sub(leftShoulder).length();

            if (distance <= distanceThreshold) {
                if (!rightHandWasOnShoulder) {
                    rightHandOnLeftShoulderStart = now;
                    rightHandWasOnShoulder = true;
                    rightHandGestureFired = false;
                } else if (!rightHandGestureFired
                        && now - rightHandOnLeftShoulderStart >= holdDurationMs
                        && now - lastRightGestureTime >= COOLDOWN_MS) {
                    fireKeyboardKey(VRClientSettings.shoulderRightToLeftKeyCode,
                            "right controller -> left shoulder");
                    lastRightGestureTime = now;
                    rightHandGestureFired = true;
                    handler.triggerHaptic(VRClientSettings.leftHanded, 30_000_000L, 0.85F, 0);
                }
            } else {
                rightHandWasOnShoulder = false;
                rightHandGestureFired = false;
            }
        } else {
            rightHandWasOnShoulder = false;
            rightHandGestureFired = false;
        }

        // Physical left controller -> right shoulder.
        if (leftEnabled && handler.isLeftGripHandTracked()) {
            Vector3f leftGrip = handler.getLeftGripHandPos();
            float distance = new Vector3f(leftGrip).sub(rightShoulder).length();

            if (distance <= distanceThreshold) {
                if (!leftHandWasOnShoulder) {
                    leftHandOnRightShoulderStart = now;
                    leftHandWasOnShoulder = true;
                    leftHandGestureFired = false;
                } else if (!leftHandGestureFired
                        && now - leftHandOnRightShoulderStart >= holdDurationMs
                        && now - lastLeftGestureTime >= COOLDOWN_MS) {
                    fireKeyboardKey(VRClientSettings.shoulderLeftToRightKeyCode,
                            "left controller -> right shoulder");
                    lastLeftGestureTime = now;
                    leftHandGestureFired = true;
                    handler.triggerHaptic(!VRClientSettings.leftHanded, 30_000_000L, 0.85F, 0);
                }
            } else {
                leftHandWasOnShoulder = false;
                leftHandGestureFired = false;
            }
        } else {
            leftHandWasOnShoulder = false;
            leftHandGestureFired = false;
        }
    }

    private void fireKeyboardKey(int keyCode, String gestureName) {
        if (keyCode <= 0) return;
        try {
            KeyBinding.setKeyBindState(keyCode, true);
            KeyBinding.onTick(keyCode);
            pendingKeyReleases.put(keyCode, System.nanoTime() + SYNTHETIC_KEY_PRESS_NANOS);
            GTNHVRLog.info("[VR Shoulder Button] %s emitted key %s (%d)",
                    gestureName, org.lwjgl.input.Keyboard.getKeyName(keyCode), keyCode);
        } catch (Throwable t) {
            GTNHVRLog.warn("[VR Shoulder Button] Could not emit key %d: %s", keyCode, t);
        }
    }

    /**
     * Called every game/VR interaction tick to release synthetic keyboard events after
     * the game has had time to process the press, independent of headset render FPS.
     */
    public void tick() {
        if (pendingKeyReleases.isEmpty()) return;
        long now = System.nanoTime();
        Iterator<Map.Entry<Integer, Long>> it = pendingKeyReleases.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            if (now >= entry.getValue()) {
                KeyBinding.setKeyBindState(entry.getKey(), false);
                it.remove();
            }
        }
    }

    private void resetGestureState() {
        rightHandWasOnShoulder = false;
        rightHandGestureFired = false;
        leftHandWasOnShoulder = false;
        leftHandGestureFired = false;
        rightHandOnLeftShoulderStart = 0L;
        leftHandOnRightShoulderStart = 0L;
    }

    public void clear() {
        resetGestureState();
        for (Integer keyCode : pendingKeyReleases.keySet()) {
            KeyBinding.setKeyBindState(keyCode, false);
        }
        pendingKeyReleases.clear();
    }
}
