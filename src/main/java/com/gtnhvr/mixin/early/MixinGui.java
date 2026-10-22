package com.gtnhvr.mixin.early;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.OpenXRRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels full-screen dark/colored backdrops (drawGradientRect) in in-game VR menus,
 * ensuring all inventories, pause screens, and GUI overlays float completely transparently
 * over the 3D VR world without dark or red rectangular shades (matching Visor 1:1).
 */
@Mixin(Gui.class)
public abstract class MixinGui {

    @Inject(method = {"drawGradientRect", "func_73733_a"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancelGradientBackdrop(int left, int top, int right, int bottom, int startColor, int endColor, CallbackInfo ci) {
        if (!OpenXRRuntime.isSessionRunning()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null && mc.currentScreen != null) {
            // Check if this gradient covers the entire screen (or close to it)
            if (left <= 0 && top <= 0 && right >= mc.currentScreen.width && bottom >= mc.currentScreen.height) {
                ci.cancel();
                return;
            }
        }

        if (com.gtnhvr.gui.VRGuiRenderer.isCapturing()) {
            com.gtnewhorizons.angelica.glsm.GLStateManager.glColorMask(true, true, true, false);
        }
    }

    @Inject(method = {"drawGradientRect", "func_73733_a"}, at = @At("TAIL"))
    private void gtnhvr$restoreGradientColorMask(int left, int top, int right, int bottom, int startColor, int endColor, CallbackInfo ci) {
        if (com.gtnhvr.gui.VRGuiRenderer.isCapturing()) {
            com.gtnewhorizons.angelica.glsm.GLStateManager.glColorMask(true, true, true, true);
        }
    }

    @Inject(method = {"drawRect", "func_73734_a"}, at = @At("HEAD"))
    private static void gtnhvr$protectAlphaInDrawRect(int left, int top, int right, int bottom, int color, CallbackInfo ci) {
        if (com.gtnhvr.gui.VRGuiRenderer.isCapturing() && ((color >>> 24) & 255) < 255) {
            com.gtnewhorizons.angelica.glsm.GLStateManager.glColorMask(true, true, true, false);
        }
    }

    @Inject(method = {"drawRect", "func_73734_a"}, at = @At("TAIL"))
    private static void gtnhvr$restoreAlphaInDrawRect(int left, int top, int right, int bottom, int color, CallbackInfo ci) {
        if (com.gtnhvr.gui.VRGuiRenderer.isCapturing()) {
            com.gtnewhorizons.angelica.glsm.GLStateManager.glColorMask(true, true, true, true);
        }
    }
}
