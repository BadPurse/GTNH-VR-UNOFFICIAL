package com.gtnhvr.gameplay.tasks;

import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.util.Vector3fHistory;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 1:1 Visor TaskSwing ported to Minecraft 1.7.10:
 * Detects physical controller swing velocity, weapon tip sweep arc,
 * real-time entity combat strikes, and physical block mining with tactile haptics.
 * Fully connected to VRClientSettings (swingSpeedThreshold, swingInCreative, worldBetterSwinging).
 */
@Lwjgl3Aware
public class VRTaskSwing {
    private static final VRTaskSwing INSTANCE = new VRTaskSwing();

    private static final float SWORD_LENGTH = 1.10F;
    private static final float TOOL_LENGTH = 0.95F;
    private static final float FIST_REACH = 0.85F;
    private static final float TIP_OFFSET = 0.30F;

    private final HandSwingData rightHand = new HandSwingData();
    private final HandSwingData leftHand = new HandSwingData();

    private int inactiveTicks = 3;
    private int miningKeepTicks = 0;

    public boolean isKeepingMining() {
        return miningKeepTicks > 0;
    }

    public static class HandSwingData {
        public final Vector3fHistory handHistory = new Vector3fHistory(120);
        public boolean lastSwingBlock = false;
        public final Set<Entity> lastHitEntities = new HashSet<>();
        public Vector3f lastHandPos = null;
        public Vector3f lastSwingPoint = null;

        public void reset() {
            lastSwingBlock = false;
            lastHitEntities.clear();
            lastHandPos = null;
            lastSwingPoint = null;
        }
    }

    private VRTaskSwing() {}

    public static VRTaskSwing getInstance() {
        return INSTANCE;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (miningKeepTicks > 0) {
            miningKeepTicks--;
        }

        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
            clear();
            return;
        }

        if (inactiveTicks > 0) {
            inactiveTicks--;
            return;
        }

        if (!VRClientSettings.swingInCreative && mc.thePlayer.capabilities.isCreativeMode) {
            return;
        }

        // Process Right Hand (Main Hand)
        if (handler.isRightHandTracked()) {
            processHandSwing(mc, handler, rightHand, true);
        }

        // Process Left Hand (Offhand)
        if (handler.isLeftHandTracked()) {
            processHandSwing(mc, handler, leftHand, false);
        }
    }

    private void processHandSwing(Minecraft mc, VRControllerHandler handler, HandSwingData data, boolean isRight) {
        Vector3f handPos = isRight ? handler.getRightHandPos() : handler.getLeftHandPos();
        Quaternionf handRot = isRight ? handler.getRightHandRot() : handler.getLeftHandRot();

        Vector3f forwardDir = handRot.transform(new Vector3f(0.0F, 0.0F, -1.0F));

        // Update velocity history
        Vector3f tipSample = new Vector3f(handPos).add(new Vector3f(forwardDir).mul(TIP_OFFSET));
        data.handHistory.add(tipSample);

        float speed = data.handHistory.averageSpeed(0.31F);
        float threshold = Math.max(0.5F, VRClientSettings.swingSpeedThreshold);

        ItemStack held = isRight ? mc.thePlayer.getHeldItem() : com.gtnhvr.util.BackhandCompat.getOffhandItem(mc.thePlayer);
        float itemLength = getItemLength(held);

        Vector3f swingPoint = new Vector3f(handPos).add(new Vector3f(forwardDir).mul(itemLength));
        Vector3f prevSwingPoint = (data.lastSwingPoint != null) ? data.lastSwingPoint : swingPoint;

        data.lastHandPos = handPos;
        data.lastSwingPoint = swingPoint;

        boolean canSwing = (speed >= threshold) && !data.lastSwingBlock;
        data.lastSwingBlock = false;

        if (speed < threshold * 0.70F) {
            data.lastHitEntities.clear();
        }

        if (!canSwing) {
            return;
        }

        // 1. Entity Combat Attack
        boolean attacked = handleEntitySwing(mc, handler, data, handPos, swingPoint, prevSwingPoint, isRight);

        // 2. Physical Block Mining
        if (!attacked) {
            handleBlockSwing(mc, handler, data, handPos, swingPoint, prevSwingPoint, speed, isRight);
        }
    }

    private boolean handleEntitySwing(Minecraft mc, VRControllerHandler handler, HandSwingData data,
                                      Vector3f handPos, Vector3f swingPoint, Vector3f prevSwingPoint, boolean isRight) {
        // Build bounding box between current and previous swing points in world coordinates
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        float baseYawRad = (float) Math.toRadians(com.gtnhvr.HeadPoseController.getBaseYaw());
        float cos = (float) Math.cos(baseYawRad);
        float sin = (float) Math.sin(baseYawRad);

        // Transform room coords to world coords
        Vec3 worldCur = roomToWorld(swingPoint, px, py, pz, cos, sin);
        Vec3 worldPrev = roomToWorld(prevSwingPoint, px, py, pz, cos, sin);

        AxisAlignedBB sweepBox = AxisAlignedBB.getBoundingBox(
                Math.min(worldCur.xCoord, worldPrev.xCoord) - 0.5D,
                Math.min(worldCur.yCoord, worldPrev.yCoord) - 0.5D,
                Math.min(worldCur.zCoord, worldPrev.zCoord) - 0.5D,
                Math.max(worldCur.xCoord, worldPrev.xCoord) + 0.5D,
                Math.max(worldCur.yCoord, worldPrev.yCoord) + 0.5D,
                Math.max(worldCur.zCoord, worldPrev.zCoord) + 0.5D
        );

        @SuppressWarnings("unchecked")
        List<Entity> targets = mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer, sweepBox);
        boolean anyAttacked = false;

        if (targets != null && !targets.isEmpty()) {
            for (Entity target : targets) {
                if (target == null || target.isDead || !target.canBeCollidedWith()) continue;
                if (target == mc.thePlayer.ridingEntity) continue;
                if (data.lastHitEntities.contains(target)) continue;

                // Attack entity!
                mc.playerController.attackEntity(mc.thePlayer, target);
                mc.thePlayer.swingItem();

                data.lastHitEntities.add(target);
                anyAttacked = true;

                // Haptic strike feedback pulse
                handler.triggerHaptic(!isRight, 10_000_000L, 0.75F, 0);
            }
        }

        return anyAttacked;
    }

    private void handleBlockSwing(Minecraft mc, VRControllerHandler handler, HandSwingData data,
                                  Vector3f handPos, Vector3f swingPoint, Vector3f prevSwingPoint,
                                  float speed, boolean isRight) {
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        float baseYawRad = (float) Math.toRadians(com.gtnhvr.HeadPoseController.getBaseYaw());
        float cos = (float) Math.cos(baseYawRad);
        float sin = (float) Math.sin(baseYawRad);

        Vec3 start = roomToWorld(handPos, px, py, pz, cos, sin);
        Vec3 end = roomToWorld(swingPoint, px, py, pz, cos, sin);

        MovingObjectPosition hit = mc.theWorld.func_147447_a(start, end, false, false, true);
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            data.lastSwingBlock = true;

            int bx = hit.blockX;
            int by = hit.blockY;
            int bz = hit.blockZ;
            int side = hit.sideHit;

            Block block = mc.theWorld.getBlock(bx, by, bz);
            if (block != null && !block.isAir(mc.theWorld, bx, by, bz)) {
                // Keep block mining damage alive across physical swings (prevents instant reset)
                miningKeepTicks = 50;

                // Strike block
                mc.playerController.clickBlock(bx, by, bz, side);
                mc.thePlayer.swingItem();

                if (VRClientSettings.worldBetterSwinging) {
                    float threshold = Math.max(0.5F, VRClientSettings.swingSpeedThreshold);
                    int totalHits = 3;
                    float overSpeed = Math.min(Math.max(0.0F, speed - threshold), 4.0F);
                    totalHits += (int) overSpeed;

                    for (int i = 0; i < totalHits; i++) {
                        mc.playerController.onPlayerDamageBlock(bx, by, bz, side);
                    }
                    // Hit sound
                    mc.theWorld.playSound(bx + 0.5D, by + 0.5D, bz + 0.5D,
                            block.stepSound.getStepResourcePath(),
                            (block.stepSound.getVolume() + 1.0F) / 8.0F,
                            block.stepSound.getPitch() * 0.5F, false);
                    // Hit dust particles
                    mc.effectRenderer.addBlockHitEffects(bx, by, bz, side);
                }

                // Block hit haptic pulse
                float intensity = Math.min(1.0F, speed / 5.0F);
                handler.triggerHaptic(!isRight, 6_000_000L, 0.40F + intensity * 0.40F, 0);
            }
        }
    }

    private static Vec3 roomToWorld(Vector3f roomPos, double px, double py, double pz, float cos, float sin) {
        return com.gtnhvr.VRControllerHandler.getInstance().roomToWorld(roomPos, 1.0F);
    }

    private static float getItemLength(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return FIST_REACH;
        }
        Item item = stack.getItem();
        if (item instanceof ItemSword) {
            return SWORD_LENGTH;
        }
        if (item instanceof ItemPickaxe || item instanceof ItemAxe || item instanceof ItemSpade) {
            return TOOL_LENGTH;
        }
        return FIST_REACH;
    }

    public void clear() {
        miningKeepTicks = 0;
        rightHand.reset();
        leftHand.reset();
        rightHand.handHistory.clear();
        leftHand.handHistory.clear();
    }
}
