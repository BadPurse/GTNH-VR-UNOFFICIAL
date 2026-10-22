package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gameplay.tasks.VRTaskSwing;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserves vanilla block-damage progress between physical VR swings.
 *
 * Do not shadow PlayerControllerMP.blockHitDelay here: GTNH's transformed 1.7.10
 * PlayerControllerMP doesn't expose that field under the expected name, and a failed
 * shadow makes Mixin reject this entire class (including resetBlockRemoving).
 */
@Mixin(PlayerControllerMP.class)
public abstract class MixinPlayerControllerMP {

    @Inject(method = {"resetBlockRemoving", "func_78767_c"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhvr$onResetBlockRemoving(CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning() && VRTaskSwing.getInstance().isKeepingMining()) {
            ci.cancel();
        }
    }
}
