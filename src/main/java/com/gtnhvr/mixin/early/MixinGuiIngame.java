package com.gtnhvr.mixin.early;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gui.VRGuiRenderer;
import com.gtnhvr.gui.VRHudRenderer;
import net.minecraft.client.Minecraft;
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

    @Inject(method = {"renderGameOverlay", "func_73830_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$beginVirtualHudCapture(float partialTicks, boolean hasScreen, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning() && mc != null && mc.theWorld != null
                && !VRHudRenderer.isCapturing()) {
            VRHudRenderer.begin(VRGuiRenderer.getCaptureWidth(), VRGuiRenderer.getCaptureHeight());
        }
    }

    @Inject(method = {"renderGameOverlay", "func_73830_a"}, at = @At("TAIL"), require = 0)
    private void gtnhvr$endVirtualHudCapture(float partialTicks, boolean hasScreen, CallbackInfo ci) {
        if (VRHudRenderer.isCapturing()) VRHudRenderer.end();
    }

    @Inject(method = {"renderVignette", "func_73829_a"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancelVanillaVignette(float light, int width, int height, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            GLStateManager.glBlendFuncSeparate(770, 771, 1, 0);
            ci.cancel();
        }
    }
}
