package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1:1 Visor movement and turnPlayer integration:
 * Suppresses desktop mouse delta from rotating the player in VR.
 * Locomotion is driven by vanilla moveFlying using rotationYaw synced by HeadPoseController.
 */
@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = {"setAngles", "func_70082_c"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhvr$suppressMouseTurning(float yaw, float pitch, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            ci.cancel();
        }
    }
}

