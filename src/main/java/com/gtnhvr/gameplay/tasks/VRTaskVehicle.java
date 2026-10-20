package com.gtnhvr.gameplay.tasks;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.util.Vector3fHistory;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.MathHelper;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrPosef;

/**
 * 1:1 Visor TaskBoat and TaskVehicle ported to Minecraft 1.7.10:
 * - Physical Boat Rowing: Swinging hands like oars propels and steers boats.
 * - Physical Vehicle Dismount: Stepping physically away (>1.0m) from any vehicle
 *   (boat, minecart, horse) automatically dismounts the player.
 * Connected directly to VRClientSettings (roomBoatRowingEnabled, roomDismountVehicleEnabled).
 */
@Lwjgl3Aware
public class VRTaskVehicle {
    private static final VRTaskVehicle INSTANCE = new VRTaskVehicle();

    private final Vector3fHistory rightHistory = new Vector3fHistory(60);
    private final Vector3fHistory leftHistory = new Vector3fHistory(60);

    private Vector3f mountRoomPos = null;
    private Entity lastRidingEntity = null;

    private VRTaskVehicle() {}

    public static VRTaskVehicle getInstance() {
        return INSTANCE;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
            clear();
            return;
        }

        Entity riding = mc.thePlayer.ridingEntity;

        if (riding == null) {
            mountRoomPos = null;
            lastRidingEntity = null;
            return;
        }

        XrPosef headPose = OpenXRRuntime.getHeadPose();
        if (headPose == null) return;

        Vector3f headPos = new Vector3f(headPose.position$().x(), headPose.position$().y(), headPose.position$().z());

        // 1. Physical Auto-Dismount
        if (VRClientSettings.roomDismountVehicleEnabled || VRClientSettings.roomDismountVehicle) {
            if (mountRoomPos == null || riding != lastRidingEntity) {
                mountRoomPos = new Vector3f(headPos.x, 0.0F, headPos.z);
                lastRidingEntity = riding;
            } else {
                float dx = headPos.x - mountRoomPos.x;
                float dz = headPos.z - mountRoomPos.z;
                float distSq = dx * dx + dz * dz;

                // If stepped > 1.0 meter away in room space from where mounted
                if (distSq > 1.0F) {
                    mc.thePlayer.mountEntity(null);
                    mountRoomPos = null;
                    lastRidingEntity = null;
                    return;
                }
            }
        }

        // 2. Physical Boat Rowing
        if ((VRClientSettings.roomBoatRowingEnabled || VRClientSettings.roomBoatRowing) && riding instanceof EntityBoat boat) {
            if (handler.isRightHandTracked() && handler.isLeftHandTracked()) {
                rightHistory.add(handler.getRightHandPos());
                leftHistory.add(handler.getLeftHandPos());

                float rightSpeed = Math.max(0.0F, rightHistory.averageSpeed(0.40F) - 0.40F);
                float leftSpeed = Math.max(0.0F, leftHistory.averageSpeed(0.40F) - 0.40F);

                if (rightSpeed > 0.10F && leftSpeed > 0.10F) {
                    // Both oars: push boat forward
                    float forwardPower = Math.min(1.20F, (rightSpeed + leftSpeed) * 0.40F);
                    float yawRad = (float) Math.toRadians(boat.rotationYaw);
                    boat.motionX += -MathHelper.sin(yawRad) * forwardPower * 0.05D;
                    boat.motionZ += MathHelper.cos(yawRad) * forwardPower * 0.05D;
                    handler.triggerHaptic(false, 3_000_000L, 0.25F, 0);
                    handler.triggerHaptic(true, 3_000_000L, 0.25F, 0);
                } else if (rightSpeed > 0.20F) {
                    // Right oar: turn left
                    boat.rotationYaw -= rightSpeed * 3.0F;
                    handler.triggerHaptic(false, 3_000_000L, 0.20F, 0);
                } else if (leftSpeed > 0.20F) {
                    // Left oar: turn right
                    boat.rotationYaw += leftSpeed * 3.0F;
                    handler.triggerHaptic(true, 3_000_000L, 0.20F, 0);
                }
            }
        }
    }

    public void clear() {
        mountRoomPos = null;
        lastRidingEntity = null;
        rightHistory.clear();
        leftHistory.clear();
    }
}
