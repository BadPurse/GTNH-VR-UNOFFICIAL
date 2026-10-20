package com.gtnhvr;

import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * 1:1 Visor VRAimPicker for Minecraft 1.7.10:
 * Casts 6-DoF rays from both VR controller hands into the world
 * to accurately target blocks, entities, and faces for mining, block placing,
 * offhand interactions, and crosshair positioning.
 */
@Lwjgl3Aware
public final class VRHandAimPicker {
    private static MovingObjectPosition rightHitResult = null;
    private static Vec3 rightAimHitPos = null;

    private static MovingObjectPosition leftHitResult = null;
    private static Vec3 leftAimHitPos = null;

    private VRHandAimPicker() {}

    public static class TraceResult {
        public MovingObjectPosition hit;
        public Vec3 aimPos;
        public Entity entity;
    }

    public static TraceResult traceRay(Minecraft mc, Vector3f handPos, Vector3f handDir, double reach) {
        TraceResult res = new TraceResult();
        if (mc == null || mc.theWorld == null) return res;

        Vec3 start = Vec3.createVectorHelper(handPos.x, handPos.y, handPos.z);
        Vec3 end = Vec3.createVectorHelper(
                handPos.x + handDir.x * reach,
                handPos.y + handDir.y * reach,
                handPos.z + handDir.z * reach
        );

        // 1. Ray trace against world blocks
        MovingObjectPosition blockHit = mc.theWorld.func_147447_a(start, end, false, false, true);
        double closestDist = reach;
        if (blockHit != null && blockHit.hitVec != null) {
            closestDist = blockHit.hitVec.distanceTo(start);
        }

        // 2. Intersect entities along aim ray up to block hit distance
        MovingObjectPosition entityHit = null;
        Vec3 reachEnd = Vec3.createVectorHelper(
                handPos.x + handDir.x * closestDist,
                handPos.y + handDir.y * closestDist,
                handPos.z + handDir.z * closestDist
        );

        AxisAlignedBB searchBox = AxisAlignedBB.getBoundingBox(
                Math.min(start.xCoord, reachEnd.xCoord) - 1.0D,
                Math.min(start.yCoord, reachEnd.yCoord) - 1.0D,
                Math.min(start.zCoord, reachEnd.zCoord) - 1.0D,
                Math.max(start.xCoord, reachEnd.xCoord) + 1.0D,
                Math.max(start.yCoord, reachEnd.yCoord) + 1.0D,
                Math.max(start.zCoord, reachEnd.zCoord) + 1.0D
        );

        @SuppressWarnings("unchecked")
        List<Entity> list = mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer, searchBox);
        Entity pointedEntity = null;
        Vec3 entityHitVec = null;
        double minEntityDist = closestDist;

        if (list != null) {
            for (Entity entity : list) {
                if (entity.canBeCollidedWith()) {
                    float border = entity.getCollisionBorderSize();
                    AxisAlignedBB bb = entity.boundingBox.expand(border, border, border);
                    MovingObjectPosition intercept = bb.calculateIntercept(start, reachEnd);

                    if (bb.isVecInside(start)) {
                        if (minEntityDist >= 0.0D) {
                            pointedEntity = entity;
                            entityHitVec = (intercept == null) ? start : intercept.hitVec;
                            minEntityDist = 0.0D;
                        }
                    } else if (intercept != null) {
                        double d = start.distanceTo(intercept.hitVec);
                        if (d < minEntityDist || minEntityDist == 0.0D) {
                            pointedEntity = entity;
                            entityHitVec = intercept.hitVec;
                            minEntityDist = d;
                        }
                    }
                }
            }
        }

        if (pointedEntity != null && (minEntityDist < closestDist || blockHit == null)) {
            entityHit = new MovingObjectPosition(pointedEntity, entityHitVec);
            res.entity = pointedEntity;
        }

        res.hit = (entityHit != null) ? entityHit : blockHit;
        if (res.hit != null && res.hit.hitVec != null) {
            res.aimPos = res.hit.hitVec;
        } else {
            res.aimPos = end;
        }
        return res;
    }

    /**
     * Performs hand raycasting for both hands and updates Minecraft's objectMouseOver.
     */
    public static void update(Minecraft mc, float partialTicks) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return;
        if (!OpenXRRuntime.isSessionRunning()) return;

        VRControllerHandler handler = VRControllerHandler.getInstance();

        double baseReach = (mc.playerController != null) ? mc.playerController.getBlockReachDistance() : 4.5D;
        double reachBase = (VRClientSettings.interactionReach > 0.0F) ? VRClientSettings.interactionReach : baseReach;
        double reach = reachBase * Math.max(0.25F, Math.min(4.0F, VRClientSettings.worldScale));

        // Right Hand Ray
        if (handler.isRightHandTracked()) {
            Vector3f rightPos = handler.getRightHandWorldPos(partialTicks);
            Vector3f rightDir = handler.getRightHandWorldDir();
            TraceResult rRes = traceRay(mc, rightPos, rightDir, reach);
            rightHitResult = rRes.hit;
            rightAimHitPos = rRes.aimPos;
        } else {
            rightHitResult = null;
            rightAimHitPos = null;
        }

        // Left Hand Ray
        if (handler.isLeftHandTracked()) {
            Vector3f leftPos = handler.getLeftHandWorldPos(partialTicks);
            Vector3f leftDir = handler.getLeftHandWorldDir();
            TraceResult lRes = traceRay(mc, leftPos, leftDir, reach);
            leftHitResult = lRes.hit;
            leftAimHitPos = lRes.aimPos;
        } else {
            leftHitResult = null;
            leftAimHitPos = null;
        }

        boolean leftActive = handler.isLeftHandActive();
        if (leftActive && leftHitResult != null) {
            mc.objectMouseOver = leftHitResult;
        } else {
            mc.objectMouseOver = rightHitResult;
        }
    }

    public static MovingObjectPosition getHitResult() {
        return getHitResult(false);
    }

    public static MovingObjectPosition getHitResult(boolean isLeft) {
        return isLeft ? leftHitResult : rightHitResult;
    }

    public static Vec3 getAimHitPos() {
        return getAimHitPos(false);
    }

    public static Vec3 getAimHitPos(boolean isLeft) {
        return isLeft ? leftAimHitPos : rightAimHitPos;
    }
}
