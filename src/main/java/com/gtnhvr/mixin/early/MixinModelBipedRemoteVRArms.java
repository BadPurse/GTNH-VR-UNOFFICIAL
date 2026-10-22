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
    @Shadow public ModelRenderer bipedLeftArm;
    @Shadow public ModelRenderer bipedRightArm;

    @Unique private boolean gtnhvr$restoreRemoteArms;
    @Unique private boolean gtnhvr$hideLeftArm;
    @Unique private boolean gtnhvr$hideRightArm;
    @Unique private boolean gtnhvr$oldLeftShowModel;
    @Unique private boolean gtnhvr$oldRightShowModel;

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
