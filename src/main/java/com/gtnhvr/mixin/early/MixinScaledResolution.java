package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gui.VRGuiProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic-only hook for ScaledResolution construction.
 *
 * Do not rewrite constructor arguments globally: arbitrary mods depend on the supplied pixel
 * space for scissor rectangles, viewports, model previews and mouse handling. Virtual dimensions
 * are now applied only to explicit GUI initialization and render scopes.
 */
@Mixin(ScaledResolution.class)
public abstract class MixinScaledResolution {
    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"), require = 0)
    private void gtnhvr$countScaledResolution(Minecraft mc, int width, int height, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            VRGuiProfiler.scaledResolutionCreated();
        }
    }
}
