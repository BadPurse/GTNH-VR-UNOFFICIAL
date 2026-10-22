package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.gameplay.tasks.VRTaskBow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the real 1.7.10 ItemBow projectile use the same controller aim as the VR trajectory.
 * Vanilla derives EntityArrow yaw/pitch from EntityPlayer inside onPlayerStoppedUsing(), so
 * temporarily apply the VR angles only while that method constructs the arrow, then restore
 * the view immediately after it returns.
 */
@Mixin(net.minecraft.item.ItemBow.class)
public abstract class MixinItemBowAim {
    private EntityPlayer gtnhvr$aimOverridePlayer;
    private float gtnhvr$oldYaw;
    private float gtnhvr$oldPitch;

    @Inject(method = {"onPlayerStoppedUsing", "func_77615_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$applyControllerBowAim(ItemStack stack, World world, EntityPlayer player,
                                               int timeLeft, CallbackInfo ci) {
        if (!OpenXRRuntime.isSessionRunning() || player == null) return;

        VRTaskBow bow = VRTaskBow.getInstance();
        if (!bow.hasRecentLaunchAim()) return;

        Vector3f aim = bow.getAimDirectionWorld(VRControllerHandler.getInstance());
        if (aim == null || aim.lengthSquared() < 1.0E-6F) return;
        aim.normalize();

        gtnhvr$aimOverridePlayer = player;
        gtnhvr$oldYaw = player.rotationYaw;
        gtnhvr$oldPitch = player.rotationPitch;

        // Minecraft's look vector is (-sin(yaw)*cos(pitch), -sin(pitch), cos(yaw)*cos(pitch)).
        player.rotationYaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        player.rotationPitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, aim.y))));
    }

    @Inject(method = {"onPlayerStoppedUsing", "func_77615_a"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$restorePlayerAim(ItemStack stack, World world, EntityPlayer player,
                                           int timeLeft, CallbackInfo ci) {
        if (gtnhvr$aimOverridePlayer == null) return;
        gtnhvr$aimOverridePlayer.rotationYaw = gtnhvr$oldYaw;
        gtnhvr$aimOverridePlayer.rotationPitch = gtnhvr$oldPitch;
        gtnhvr$aimOverridePlayer = null;
    }
}
