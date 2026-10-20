package com.gtnhvr.gameplay.tasks;

import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.util.Vector3fHistory;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityBoat;
import org.joml.Vector3f;

/**
 * 1:1 Visor TaskBoat ported to Minecraft 1.7.10:
 * Allows realistic physical rowing of boats in VR by making circular oar strokes
 * with both controllers along the sides of the boat.
 * Stroking both hands moves the boat forward; stroking one hand turns the boat.
 */
@Lwjgl3Aware
public class VRTaskBoat {
    private static final VRTaskBoat INSTANCE = new VRTaskBoat();

    private static final float MAX_FORWARD_SPEED = 2.0F;
    private static final float MAX_ROTATION_SPEED = 1.0F;
    private static final float SPEED_OFFSET = 0.40F;
    private static final float FORWARD_DISABLE_THRESHOLD = 0.25F;

    private final Vector3fHistory rightHandHistory = new Vector3fHistory(60);
    private final Vector3fHistory leftHandHistory = new Vector3fHistory(60);

    private float oarLeft = 0.0F;
    private float oarRight = 0.0F;
    private float moveForward = 0.0F;

    private VRTaskBoat() {}

    public static VRTaskBoat getInstance() {
        return INSTANCE;
    }

    public boolean isRowing() {
        return (oarRight + oarLeft + moveForward) > 0.10F;
    }

    public float getMoveForward() {
        return moveForward;
    }

    public float getOarLeft() {
        return oarLeft;
    }

    public float getOarRight() {
        return oarRight;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
            clear();
            return;
        }

        if (!VRClientSettings.roomBoatRowingEnabled || !(mc.thePlayer.ridingEntity instanceof EntityBoat)) {
            clear();
            return;
        }

        EntityBoat boat = (EntityBoat) mc.thePlayer.ridingEntity;

        if (handler.isRightHandTracked()) {
            rightHandHistory.add(handler.getRightHandPos());
        }
        if (handler.isLeftHandTracked()) {
            leftHandHistory.add(handler.getLeftHandPos());
        }

        float rightSpeed = rightHandHistory.averageSpeed(0.40F);
        float leftSpeed = leftHandHistory.averageSpeed(0.40F);

        this.oarRight = Math.max(0.0F, rightSpeed - SPEED_OFFSET);
        this.oarLeft = Math.max(0.0F, leftSpeed - SPEED_OFFSET);

        // Clamp oar speeds to maximum rotation speed
        if (this.oarRight > MAX_ROTATION_SPEED) this.oarRight = MAX_ROTATION_SPEED;
        if (this.oarLeft > MAX_ROTATION_SPEED) this.oarLeft = MAX_ROTATION_SPEED;

        // Move forward when both oars are stroking
        this.moveForward = (this.oarRight > 0.05F && this.oarLeft > 0.05F)
                ? (this.oarRight + this.oarLeft) * 0.5F : 0.0F;

        if (this.moveForward > MAX_FORWARD_SPEED) {
            this.moveForward = MAX_FORWARD_SPEED;
        }

        // Apply physical propulsion directly to boat in Minecraft 1.7.10
        if (this.moveForward > FORWARD_DISABLE_THRESHOLD) {
            double yawRad = Math.toRadians(boat.rotationYaw);
            double fx = -Math.sin(yawRad) * this.moveForward * 0.045D;
            double fz =  Math.cos(yawRad) * this.moveForward * 0.045D;
            boat.motionX += fx;
            boat.motionZ += fz;

            // Tactile water stroke vibration
            if (mc.thePlayer.ticksExisted % 4 == 0) {
                handler.triggerHaptic(false, 4_000_000L, 0.35F, 0);
                handler.triggerHaptic(true, 4_000_000L, 0.35F, 0);
            }
        } else if (this.oarRight > 0.08F || this.oarLeft > 0.08F) {
            // Turning stroke
            float turnDiff = this.oarRight - this.oarLeft;
            boat.rotationYaw += turnDiff * 2.8F;

            if (this.oarRight > 0.08F && mc.thePlayer.ticksExisted % 5 == 0) {
                handler.triggerHaptic(false, 3_000_000L, 0.30F, 0);
            }
            if (this.oarLeft > 0.08F && mc.thePlayer.ticksExisted % 5 == 0) {
                handler.triggerHaptic(true, 3_000_000L, 0.30F, 0);
            }
        }
    }

    public void clear() {
        rightHandHistory.clear();
        leftHandHistory.clear();
        this.oarLeft = 0.0F;
        this.oarRight = 0.0F;
        this.moveForward = 0.0F;
    }
}
