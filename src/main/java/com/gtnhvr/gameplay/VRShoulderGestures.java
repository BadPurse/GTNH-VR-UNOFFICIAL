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

    private long rightHandOnLeftShoulderStart;
    private boolean rightHandWasOnShoulder;
    private boolean rightHandGestureFired;
    private long lastRightGestureTime;

    private long leftHandOnRightShoulderStart;
    private boolean leftHandWasOnShoulder;
    private boolean leftHandGestureFired;
    private long lastLeftGestureTime;

    // The selected key remains held while the controller stays at the shoulder. This
    // makes shoulder gestures work as actual buttons and permits modifier chords such as
    // Shift+Alt when both gestures are held at once.
    private int rightGestureHeldKeyCode = 0;
    private int leftGestureHeldKeyCode = 0;

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
        if (!VRClientSettings.shoulderGesturesEnabled
                || mc == null || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            clear();
            return;
        }

        boolean rightEnabled = VRClientSettings.shoulderRightToLeftEnabled
                && VRClientSettings.shoulderRightToLeftKeyCode > 0;
        boolean leftEnabled = VRClientSettings.shoulderLeftToRightEnabled
                && VRClientSettings.shoulderLeftToRightKeyCode > 0;

        if (!rightEnabled) {
            releaseGestureKey(true);
            rightHandWasOnShoulder = false;
            rightHandGestureFired = false;
        }
        if (!leftEnabled) {
            releaseGestureKey(false);
            leftHandWasOnShoulder = false;
            leftHandGestureFired = false;
        }
        if (!rightEnabled && !leftEnabled) return;

        XrPosef headPose = OpenXRRuntime.getHeadPose();
        if (headPose == null) {
            clear();
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
                    pressShoulderKey(VRClientSettings.shoulderRightToLeftKeyCode,
                            "right controller -> left shoulder", true);
                    lastRightGestureTime = now;
                    rightHandGestureFired = true;
                    handler.triggerHaptic(VRClientSettings.leftHanded, 30_000_000L, 0.85F, 0);
                }
            } else {
                releaseGestureKey(true);
                rightHandWasOnShoulder = false;
                rightHandGestureFired = false;
            }
        } else {
            releaseGestureKey(true);
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
                    pressShoulderKey(VRClientSettings.shoulderLeftToRightKeyCode,
                            "left controller -> right shoulder", false);
                    lastLeftGestureTime = now;
                    leftHandGestureFired = true;
                    handler.triggerHaptic(!VRClientSettings.leftHanded, 30_000_000L, 0.85F, 0);
                }
            } else {
                releaseGestureKey(false);
                leftHandWasOnShoulder = false;
                leftHandGestureFired = false;
            }
        } else {
            releaseGestureKey(false);
            leftHandWasOnShoulder = false;
            leftHandGestureFired = false;
        }
    }

    private void pressShoulderKey(int keyCode, String gestureName, boolean rightSide) {
        if (keyCode <= 0) return;

        int currentKey = rightSide ? rightGestureHeldKeyCode : leftGestureHeldKeyCode;
        if (currentKey == keyCode) {
            KeyBinding.setKeyBindState(keyCode, true);
            return;
        }
        if (currentKey > 0) releaseGestureKey(rightSide);

        if (rightSide) rightGestureHeldKeyCode = keyCode;
        else leftGestureHeldKeyCode = keyCode;

        KeyBinding.setKeyBindState(keyCode, true);
        KeyBinding.onTick(keyCode);
        GTNHVRLog.info("[VR Shoulder Button] %s holding key %s (%d) while at shoulder",
                gestureName, org.lwjgl.input.Keyboard.getKeyName(keyCode), keyCode);
    }

    private void releaseGestureKey(boolean rightSide) {
        int keyCode = rightSide ? rightGestureHeldKeyCode : leftGestureHeldKeyCode;
        if (rightSide) rightGestureHeldKeyCode = 0;
        else leftGestureHeldKeyCode = 0;

        if (keyCode <= 0) return;
        boolean heldByOtherShoulder = rightGestureHeldKeyCode == keyCode || leftGestureHeldKeyCode == keyCode;
        if (!heldByOtherShoulder && !VRControllerHandler.getInstance().isControllerMappedKeyHeld(keyCode)) {
            KeyBinding.setKeyBindState(keyCode, false);
        }
    }

    /**
     * Called every VR interaction tick to preserve the pressed state between render frames.
     * The key is released when the hand leaves the shoulder, so two gestures can form a chord.
     */
    public void tick() {
        if (rightGestureHeldKeyCode > 0) KeyBinding.setKeyBindState(rightGestureHeldKeyCode, true);
        if (leftGestureHeldKeyCode > 0) KeyBinding.setKeyBindState(leftGestureHeldKeyCode, true);
    }

    /** True while an opposite-shoulder gesture is holding the supplied key down. */
    public boolean isSyntheticKeyHeld(int keyCode) {
        return keyCode > 0 && (rightGestureHeldKeyCode == keyCode || leftGestureHeldKeyCode == keyCode);
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

        int rightKey = rightGestureHeldKeyCode;
        int leftKey = leftGestureHeldKeyCode;
        rightGestureHeldKeyCode = 0;
        leftGestureHeldKeyCode = 0;

        if (rightKey > 0 && rightKey != leftKey
                && !VRControllerHandler.getInstance().isControllerMappedKeyHeld(rightKey)) {
            KeyBinding.setKeyBindState(rightKey, false);
        }
        if (leftKey > 0 && !VRControllerHandler.getInstance().isControllerMappedKeyHeld(leftKey)) {
            KeyBinding.setKeyBindState(leftKey, false);
        }
    }
}
