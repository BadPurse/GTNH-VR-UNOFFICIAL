package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla vignette rendering in VR HUD FBO (1:1 Visor GuiMixin).
 * Prevents full-screen dark vignette from tinting the floating VR HUD quad.
 */
@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame {

    @Inject(method = {"renderVignette", "func_73829_a"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancelVanillaVignette(float light, int width, int height, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            net.minecraft.client.renderer.OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            ci.cancel();
        }
    }
}
