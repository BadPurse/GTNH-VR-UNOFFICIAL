package com.gtnhvr.mixin.early;

import com.gtnhvr.network.VRBowServerState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the received controller aim only while ItemBow creates the projectile on the logical
 * server. It deliberately has no references to OpenXR or client classes.
 */
@Mixin(ItemBow.class)
public abstract class MixinItemBowAimServer {
    private EntityPlayer gtnhvr$aimOverridePlayer;
    private float gtnhvr$oldYaw;
    private float gtnhvr$oldPitch;

    @Inject(method = {"onPlayerStoppedUsing", "func_77615_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$applyServerBowAim(ItemStack stack, World world, EntityPlayer player,
                                          int timeLeft, CallbackInfo ci) {
        if (world == null || world.isRemote || player == null) return;
        VRBowServerState.LaunchState state = VRBowServerState.getRecent(player);
        if (state == null) return;

        gtnhvr$aimOverridePlayer = player;
        gtnhvr$oldYaw = player.rotationYaw;
        gtnhvr$oldPitch = player.rotationPitch;
        player.rotationYaw = state.yaw;
        player.rotationPitch = state.pitch;
    }

    @Inject(method = {"onPlayerStoppedUsing", "func_77615_a"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$restoreServerBowAim(ItemStack stack, World world, EntityPlayer player,
                                            int timeLeft, CallbackInfo ci) {
        if (gtnhvr$aimOverridePlayer != null) {
            gtnhvr$aimOverridePlayer.rotationYaw = gtnhvr$oldYaw;
            gtnhvr$aimOverridePlayer.rotationPitch = gtnhvr$oldPitch;
            VRBowServerState.clear(gtnhvr$aimOverridePlayer);
            gtnhvr$aimOverridePlayer = null;
        } else if (world != null && !world.isRemote && player != null) {
            VRBowServerState.clear(player);
        }
    }
}
