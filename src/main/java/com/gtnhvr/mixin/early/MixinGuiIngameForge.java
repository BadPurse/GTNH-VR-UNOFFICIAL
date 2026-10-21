package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gui.VRGuiRenderer;
import com.gtnhvr.gui.VRHudRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 1.7.10 renders the actual in-game HUD through GuiIngameForge, which overrides
 * GuiIngame.renderGameOverlay. Begin capture before Forge constructs RenderGameOverlayEvent's
 * ScaledResolution, otherwise every HUD listener lays itself out for the physical desktop window
 * and then draws into our differently sized virtual framebuffer.
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.client.GuiIngameForge")
public abstract class MixinGuiIngameForge {

    @Inject(method = {"renderGameOverlay", "func_73830_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$beginVirtualHudCapture(float partialTicks, boolean hasScreen,
                                                int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning() && mc != null && mc.theWorld != null
                && !VRHudRenderer.isCapturing()) {
            VRHudRenderer.begin(VRGuiRenderer.getCaptureWidth(), VRGuiRenderer.getCaptureHeight());
        }
    }

    @Inject(method = {"renderGameOverlay", "func_73830_a"}, at = @At("TAIL"), require = 0)
    private void gtnhvr$endVirtualHudCapture(float partialTicks, boolean hasScreen,
                                              int mouseX, int mouseY, CallbackInfo ci) {
        if (VRHudRenderer.isCapturing()) {
            VRHudRenderer.end();
        }
    }
}
