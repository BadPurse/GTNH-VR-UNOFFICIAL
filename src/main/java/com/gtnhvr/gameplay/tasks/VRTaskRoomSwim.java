package com.gtnhvr.gameplay.tasks;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrPosef;

/**
 * 1:1 Visor TaskRoomSwim ported to Minecraft 1.7.10:
 * Detects physical swimming breaststrokes when in water.
 * Pushing hands back against the water propels the player forward and upward.
 * Connected directly to VRClientSettings (roomSwimEnabled).
 */
@Lwjgl3Aware
public class VRTaskRoomSwim {
    private static final VRTaskRoomSwim INSTANCE = new VRTaskRoomSwim();

    private static final float STROKE_GAIN = 1.30F;
    private static final float SWIM_SCALE = 0.12F;
    private static final float MAX_SWIM_SPEED = 0.15F;
    private static final float RETENTION = 0.88F;

    private final Vector3f motion = new Vector3f();
    private float lastDistance = 0.0F;

    private VRTaskRoomSwim() {}

    public static VRTaskRoomSwim getInstance() {
        return INSTANCE;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (!VRClientSettings.roomSwimEnabled && !VRClientSettings.roomSwim) {
            clear();
            return;
        }

        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
            clear();
            return;
        }

        if (!mc.thePlayer.isInWater()) {
            clear();
            return;
        }

        if (!handler.isRightHandTracked() || !handler.isLeftHandTracked()) {
            clear();
            return;
        }

        XrPosef headPose = OpenXRRuntime.getHeadPose();
        if (headPose == null) return;

        Vector3f chestPos = new Vector3f(
                headPose.position$().x(),
                headPose.position$().y() - 0.30F,
                headPose.position$().z()
        );

        Vector3f rightPos = handler.getRightHandPos();
        Vector3f leftPos = handler.getLeftHandPos();

        // Midpoint between both hands
        Vector3f handsMid = new Vector3f(rightPos).add(leftPos).mul(0.5F);
        float currentDist = chestPos.distance(handsMid);

        float delta = lastDistance - currentDist;
        this.lastDistance = currentDist;

        // If hands are pulling backward toward/past the body (breaststroke pull)
        if (delta > 0.015F && delta < 0.50F) {
            // Direction from chest towards hands (stroke direction)
            Vector3f forwardDir = handler.getRightHandRot().transform(new Vector3f(0.0F, 0.0F, -1.0F));

            float baseYawRad = (float) Math.toRadians(com.gtnhvr.HeadPoseController.getBaseYaw());
            float cos = (float) Math.cos(baseYawRad);
            float sin = (float) Math.sin(baseYawRad);

            float worldDirX = -forwardDir.x * cos - forwardDir.z * sin;
            float worldDirY = forwardDir.y + 0.10F; // slight upward lift
            float worldDirZ = forwardDir.x * sin - forwardDir.z * cos;

            float power = delta * STROKE_GAIN * SWIM_SCALE;
            motion.add(worldDirX * power, worldDirY * power, worldDirZ * power);

            float len = motion.length();
            if (len > MAX_SWIM_SPEED) {
                motion.normalize().mul(MAX_SWIM_SPEED);
            }

            mc.thePlayer.motionX += motion.x;
            mc.thePlayer.motionY += motion.y;
            mc.thePlayer.motionZ += motion.z;

            // Gentle swimming haptic pulse
            handler.triggerHaptic(false, 3_000_000L, 0.25F, 0);
            handler.triggerHaptic(true, 3_000_000L, 0.25F, 0);
        }

        motion.mul(RETENTION);
    }

    public void clear() {
        motion.set(0.0F, 0.0F, 0.0F);
        lastDistance = 0.0F;
    }
}
