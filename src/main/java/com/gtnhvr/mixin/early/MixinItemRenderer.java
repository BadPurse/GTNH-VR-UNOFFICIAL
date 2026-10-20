package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {

    @Inject(method = {"renderItemInFirstPerson", "func_78440_a"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancelFlatHand(float partialTicks, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            ci.cancel();
        }
    }
}
