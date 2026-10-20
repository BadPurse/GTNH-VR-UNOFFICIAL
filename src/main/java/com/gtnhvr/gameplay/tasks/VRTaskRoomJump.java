package com.gtnhvr.gameplay.tasks;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.util.Vector3fHistory;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrPosef;

/**
 * 1:1 Visor TaskRoomJump ported to Minecraft 1.7.10:
 * Detects real-life jumping motion from the VR headset using vertical velocity history.
 * Triggers the in-game jump when the player jumps physically in their room space.
 * Connected directly to VRClientSettings (roomJumpEnabled, roomJumpThreshold).
 */
@Lwjgl3Aware
public class VRTaskRoomJump {
    private static final VRTaskRoomJump INSTANCE = new VRTaskRoomJump();

    private final Vector3fHistory headHistory = new Vector3fHistory(60);
    private boolean jumpCooldown = false;

    private VRTaskRoomJump() {}

    public static VRTaskRoomJump getInstance() {
        return INSTANCE;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (!VRClientSettings.roomJumpEnabled && !VRClientSettings.roomJump) {
            headHistory.clear();
            return;
        }

        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
            headHistory.clear();
            return;
        }

        XrPosef headPose = OpenXRRuntime.getHeadPose();
        if (headPose == null) return;

        Vector3f headPos = new Vector3f(headPose.position$().x(), headPose.position$().y(), headPose.position$().z());
        headHistory.add(headPos);

        if (!mc.thePlayer.onGround || mc.thePlayer.isInWater() || mc.thePlayer.ridingEntity != null) {
            jumpCooldown = false;
            return;
        }

        float upDelta = headHistory.netMovement(0.12F).y;
        float currentHeight = headPos.y;
        float baseHeight = VRClientSettings.playerHeight;
        float ratio = currentHeight / Math.max(1.0F, baseHeight);

        boolean jumpTriggered = (upDelta > 0.08F && ratio >= (VRClientSettings.roomJumpThreshold * 0.95F));

        if (jumpTriggered) {
            if (!jumpCooldown) {
                jumpCooldown = true;
                mc.thePlayer.jump();
                handler.triggerHaptic(false, 6_000_000L, 0.40F, 0);
                handler.triggerHaptic(true, 6_000_000L, 0.40F, 0);
            }
        } else if (upDelta < 0.02F) {
            jumpCooldown = false;
        }
    }

    public void clear() {
        headHistory.clear();
        jumpCooldown = false;
    }
}
