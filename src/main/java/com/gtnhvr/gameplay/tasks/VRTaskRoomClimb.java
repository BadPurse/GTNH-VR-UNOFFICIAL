package com.gtnhvr.gameplay.tasks;

import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockVine;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import org.joml.Vector3f;

/**
 * 1:1 Visor TaskRoomClimb ported to Minecraft 1.7.10:
 * Allows the player to physically grab ladders, ropes, and vines using the controller Grip button
 * and pull themselves up hand-over-hand with realistic 1:1 motion and grab haptics.
 * Cancels fall damage and gravity while gripping.
 * Connected directly to VRClientSettings (roomClimbEnabled).
 */
@Lwjgl3Aware
public class VRTaskRoomClimb {
    private static final VRTaskRoomClimb INSTANCE = new VRTaskRoomClimb();

    private boolean rightGripping = false;
    private boolean leftGripping = false;

    private float rightAnchorY = 0.0F;
    private float leftAnchorY = 0.0F;

    private boolean isClimbing = false;
    private double pendingMotionY = 0.0D;
    private int lastAppliedTick = -1;

    private VRTaskRoomClimb() {}

    public static VRTaskRoomClimb getInstance() {
        return INSTANCE;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (!VRClientSettings.roomClimbEnabled && !VRClientSettings.roomClimb) {
            clear(mc);
            return;
        }

        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
            clear(mc);
            return;
        }

        boolean nearClimbable = isPlayerNearClimbable(mc);

        float rightGrip = handler.getRightGripValue();
        float leftGrip = handler.getLeftGripValue();

        Vector3f rightPos = handler.getRightHandPos();
        Vector3f leftPos = handler.getLeftHandPos();

        boolean anyGrip = false;

        // Right Hand Grip
        if (nearClimbable && rightGrip > 0.40F && handler.isRightHandTracked()) {
            if (!rightGripping) {
                rightGripping = true;
                rightAnchorY = rightPos.y;
                handler.triggerHaptic(false, 8_000_000L, 0.60F, 0); // Grab haptic!
            } else {
                float dy = rightPos.y - rightAnchorY;
                rightAnchorY = rightPos.y;
                // Move player opposite to hand motion
                if (Math.abs(dy) > 0.001F) {
                    pendingMotionY += -dy * climbScale();
                    mc.thePlayer.fallDistance = 0.0F;
                }
            }
            anyGrip = true;
        } else {
            rightGripping = false;
        }

        // Left Hand Grip
        if (nearClimbable && leftGrip > 0.40F && handler.isLeftHandTracked()) {
            if (!leftGripping) {
                leftGripping = true;
                leftAnchorY = leftPos.y;
                handler.triggerHaptic(true, 8_000_000L, 0.60F, 0); // Grab haptic!
            } else {
                float dy = leftPos.y - leftAnchorY;
                leftAnchorY = leftPos.y;
                if (Math.abs(dy) > 0.001F) {
                    pendingMotionY += -dy * climbScale();
                    mc.thePlayer.fallDistance = 0.0F;
                }
            }
            anyGrip = true;
        } else {
            leftGripping = false;
        }

        isClimbing = anyGrip;
        if (isClimbing) {
            mc.thePlayer.fallDistance = 0.0F;
            // Hand movement is collected every frame and applied once per game tick,
            // so the climbing speed does not depend on the frame rate.
            if (mc.thePlayer.ticksExisted != lastAppliedTick) {
                lastAppliedTick = mc.thePlayer.ticksExisted;
                mc.thePlayer.motionY = Math.max(-0.6D, Math.min(0.6D, pendingMotionY));
                pendingMotionY = 0.0D;
            }
            if (mc.thePlayer.motionY < 0.0D) {
                mc.thePlayer.motionY = 0.0D; // Hold in place against gravity when gripped!
            }
        }
    }

    private boolean isPlayerNearClimbable(Minecraft mc) {
        int minX = MathHelper.floor_double(mc.thePlayer.boundingBox.minX - 0.35D);
        int maxX = MathHelper.floor_double(mc.thePlayer.boundingBox.maxX + 0.35D);
        int minY = MathHelper.floor_double(mc.thePlayer.boundingBox.minY - 0.20D);
        int maxY = MathHelper.floor_double(mc.thePlayer.boundingBox.maxY + 0.50D);
        int minZ = MathHelper.floor_double(mc.thePlayer.boundingBox.minZ - 0.35D);
        int maxZ = MathHelper.floor_double(mc.thePlayer.boundingBox.maxZ + 0.35D);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = mc.theWorld.getBlock(x, y, z);
                    if (block instanceof BlockLadder || block instanceof BlockVine ||
                            block.isLadder(mc.theWorld, x, y, z, mc.thePlayer)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double climbScale() {
        return Math.max(0.25F, Math.min(4.0F, VRClientSettings.worldScale));
    }

    public boolean isClimbing() {
        return isClimbing;
    }

    public void clear(Minecraft mc) {
        rightGripping = false;
        leftGripping = false;
        isClimbing = false;
        pendingMotionY = 0.0D;
        lastAppliedTick = -1;
    }
}
