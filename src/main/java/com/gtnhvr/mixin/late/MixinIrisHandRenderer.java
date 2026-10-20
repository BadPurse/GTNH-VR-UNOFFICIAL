package com.gtnhvr.mixin.late;

import com.gtnhvr.OpenXRRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With shaders enabled Angelica's Iris port draws the first-person hand itself (solid and
 * translucent passes, including the Backhand off hand) instead of going through
 * EntityRenderer.renderHand. Those passes are skipped while the VR session is active.
 */
@Pseudo
@Mixin(targets = "net.coderbot.iris.pipeline.HandRenderer")
public abstract class MixinIrisHandRenderer {

    @Inject(method = "renderSolid", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gtnhvr$skipSolidHand(CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTranslucent", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gtnhvr$skipTranslucentHand(CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            ci.cancel();
        }
    }
}
