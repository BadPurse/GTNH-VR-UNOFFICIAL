package com.gtnhvr.mixin.early;

import com.gtnhvr.network.VRPoseData;
import com.gtnhvr.network.VRRemotePoseClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents duplicate vanilla arms when matching remote hands are drawn at their networked VR poses. */
@Mixin(ModelBiped.class)
public abstract class MixinModelBipedRemoteVRArms {
    @Shadow public ModelRenderer bipedHead;
    @Shadow public ModelRenderer bipedHeadwear;
    @Shadow public ModelRenderer bipedLeftArm;
    @Shadow public ModelRenderer bipedRightArm;

    @Unique private boolean gtnhvr$restoreRemoteArms;
    @Unique private boolean gtnhvr$hideLeftArm;
    @Unique private boolean gtnhvr$hideRightArm;
    @Unique private boolean gtnhvr$oldLeftShowModel;
    @Unique private boolean gtnhvr$oldRightShowModel;
    @Unique private boolean gtnhvr$headPositionAdjusted;
    @Unique private float gtnhvr$headPointX, gtnhvr$headPointY, gtnhvr$headPointZ;
    @Unique private float gtnhvr$hatPointX, gtnhvr$hatPointY, gtnhvr$hatPointZ;

    @Inject(method = {"setRotationAngles", "func_78087_a"}, at = @At("TAIL"), require = 0)
    private void gtnhvr$applyRemoteHeadPose(float limbSwing, float limbSwingAmount, float ageInTicks,
                                             float netHeadYaw, float headPitch, float scale, Entity entity,
                                             CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer)) return;
        Minecraft mc = Minecraft.getMinecraft();
        AbstractClientPlayer player = (AbstractClientPlayer) entity;
        if (mc == null || player == mc.thePlayer) return;

        VRPoseData pose = VRRemotePoseClientState.getInterpolated(player.getUniqueID());
        if (pose == null || (pose.flags & VRPoseData.HEAD_TRACKED) == 0) return;

        float relativeYaw = pose.headYaw - player.renderYawOffset;
        while (relativeYaw > 180.0F) relativeYaw -= 360.0F;
        while (relativeYaw < -180.0F) relativeYaw += 360.0F;
        bipedHead.rotateAngleY = (float) Math.toRadians(relativeYaw);
        bipedHead.rotateAngleX = (float) Math.toRadians(pose.headPitch);
    }

    /**
     * Applies only the HMD's room-scale positional offset to the remote head model.
     * Entity coordinates, collision bounds and server movement are deliberately untouched.
     */
    @Inject(method = {"render", "func_78088_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$applyRemoteHeadPosition(Entity entity, float limbSwing, float limbSwingAmount,
                                                float ageInTicks, float netHeadYaw, float headPitch,
                                                float scale, CallbackInfo ci) {
        gtnhvr$headPositionAdjusted = false;
        if (!(entity instanceof AbstractClientPlayer)) return;

        Minecraft mc = Minecraft.getMinecraft();
        AbstractClientPlayer player = (AbstractClientPlayer) entity;
        if (mc == null || player == mc.thePlayer) return;

        VRPoseData pose = VRRemotePoseClientState.getInterpolated(player.getUniqueID());
        if (pose == null || (pose.flags & VRPoseData.HEAD_TRACKED) == 0) return;

        float dx = pose.headX - (float) player.posX;
        float dy = pose.headY - ((float) player.posY + player.getEyeHeight() + 0.13F);
        float dz = pose.headZ - (float) player.posZ;
        // A room-scale head normally stays within about a metre of the entity. Ignore obviously
        // stale/malicious offsets instead of letting a client detach a head across the scene.
        if (Math.abs(dx) > 1.5F || Math.abs(dy) > 1.5F || Math.abs(dz) > 1.5F) return;

        float yaw = (float) Math.toRadians(player.renderYawOffset);
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        float localX = 16.0F * (cos * dx + sin * dz);
        float localY = -16.0F * dy;
        float localZ = 16.0F * (sin * dx - cos * dz);

        gtnhvr$headPointX = bipedHead.rotationPointX;
        gtnhvr$headPointY = bipedHead.rotationPointY;
        gtnhvr$headPointZ = bipedHead.rotationPointZ;
        bipedHead.rotationPointX += localX;
        bipedHead.rotationPointY += localY;
        bipedHead.rotationPointZ += localZ;

        if (bipedHeadwear != null) {
            gtnhvr$hatPointX = bipedHeadwear.rotationPointX;
            gtnhvr$hatPointY = bipedHeadwear.rotationPointY;
            gtnhvr$hatPointZ = bipedHeadwear.rotationPointZ;
            bipedHeadwear.rotationPointX += localX;
            bipedHeadwear.rotationPointY += localY;
            bipedHeadwear.rotationPointZ += localZ;
        }
        gtnhvr$headPositionAdjusted = true;
    }

    @Inject(method = {"render", "func_78088_a"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$restoreRemoteHeadPosition(Entity entity, float limbSwing, float limbSwingAmount,
                                                  float ageInTicks, float netHeadYaw, float headPitch,
                                                  float scale, CallbackInfo ci) {
        if (!gtnhvr$headPositionAdjusted) return;
        bipedHead.rotationPointX = gtnhvr$headPointX;
        bipedHead.rotationPointY = gtnhvr$headPointY;
        bipedHead.rotationPointZ = gtnhvr$headPointZ;
        if (bipedHeadwear != null) {
            bipedHeadwear.rotationPointX = gtnhvr$hatPointX;
            bipedHeadwear.rotationPointY = gtnhvr$hatPointY;
            bipedHeadwear.rotationPointZ = gtnhvr$hatPointZ;
        }
        gtnhvr$headPositionAdjusted = false;
    }

    @Inject(method = {"render", "func_78088_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$hideTrackedRemoteArms(Entity entity, float limbSwing, float limbSwingAmount,
                                               float ageInTicks, float netHeadYaw, float headPitch,
                                               float scale, CallbackInfo ci) {
        gtnhvr$restoreRemoteArms = false;
        gtnhvr$hideLeftArm = false;
        gtnhvr$hideRightArm = false;
        if (!(entity instanceof AbstractClientPlayer)) return;

        Minecraft mc = Minecraft.getMinecraft();
        AbstractClientPlayer player = (AbstractClientPlayer) entity;
        if (mc == null || player == mc.thePlayer) return;

        VRPoseData pose = VRRemotePoseClientState.getInterpolated(player.getUniqueID());
        if (pose == null || pose.flags == 0) return;

        gtnhvr$hideLeftArm = (pose.flags & VRPoseData.LEFT_HAND_TRACKED) != 0;
        gtnhvr$hideRightArm = (pose.flags & VRPoseData.RIGHT_HAND_TRACKED) != 0;
        if (!gtnhvr$hideLeftArm && !gtnhvr$hideRightArm) return;

        gtnhvr$oldLeftShowModel = bipedLeftArm.showModel;
        gtnhvr$oldRightShowModel = bipedRightArm.showModel;
        if (gtnhvr$hideLeftArm) bipedLeftArm.showModel = false;
        if (gtnhvr$hideRightArm) bipedRightArm.showModel = false;
        gtnhvr$restoreRemoteArms = true;
    }

    @Inject(method = {"render", "func_78088_a"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$restoreTrackedRemoteArms(Entity entity, float limbSwing, float limbSwingAmount,
                                                  float ageInTicks, float netHeadYaw, float headPitch,
                                                  float scale, CallbackInfo ci) {
        if (!gtnhvr$restoreRemoteArms) return;
        if (gtnhvr$hideLeftArm) bipedLeftArm.showModel = gtnhvr$oldLeftShowModel;
        if (gtnhvr$hideRightArm) bipedRightArm.showModel = gtnhvr$oldRightShowModel;
        gtnhvr$restoreRemoteArms = false;
        gtnhvr$hideLeftArm = false;
        gtnhvr$hideRightArm = false;
    }
}
