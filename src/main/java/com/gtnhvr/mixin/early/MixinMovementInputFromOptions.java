package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1:1 Visor MovementInput integration (MovementInputMixin):
 * Directly injects VR analog impulses into updatePlayerMoveState,
 * ensuring movement perfectly tracks headset gaze in real-time without keyboard key-clobbering.
 */
@Mixin(MovementInputFromOptions.class)
public abstract class MixinMovementInputFromOptions extends MovementInput {

    @Inject(method = {"updatePlayerMoveState", "func_78898_a"}, at = @At("TAIL"))
    private void gtnhvr$applyVRMovementState(CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            VRControllerHandler.getInstance().applyVRMovement(this);
        }
    }
}
