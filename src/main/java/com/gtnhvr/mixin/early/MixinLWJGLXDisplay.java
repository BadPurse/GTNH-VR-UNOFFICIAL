package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures org.lwjglx.opengl.Display.isActive() returns true while VR session is active,
 * preventing vanilla Minecraft from auto-pausing or dropping focus when the desktop window loses OS focus.
 */
@Pseudo
@Mixin(targets = {"org.lwjglx.opengl.Display", "org.lwjgl.opengl.Display"}, remap = false)
public abstract class MixinLWJGLXDisplay {

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$isActive(CallbackInfoReturnable<Boolean> cir) {
        if (OpenXRRuntime.isSessionRunning()) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Visor keeps GUI rendering on its own fixed GUI canvas by overriding Window's
     * pixel dimensions during the GUI render phase. In 1.7.10/LWJGL3ify the equivalent
     * is Display.getWidth/getHeight. Do not affect game rendering or desktop input:
     * these values change only while VRGuiRenderer is laying out/rendering a screen.
     */
    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$virtualGuiWidth(CallbackInfoReturnable<Integer> cir) {
        if (com.gtnhvr.gui.VRGuiRenderer.isLogicalGuiResolutionActive()) {
            int width = com.gtnhvr.gui.VRGuiRenderPipeline.getActiveTargetWidth();
            if (width <= 0) width = com.gtnhvr.gui.VRGuiRenderer.getCaptureWidth();
            if (com.gtnhvr.gui.VRHudRenderer.isCapturing()) width = com.gtnhvr.gui.VRHudRenderer.getWidth();
            if (width > 0) {
                cir.setReturnValue(width);
            }
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$virtualGuiHeight(CallbackInfoReturnable<Integer> cir) {
        if (com.gtnhvr.gui.VRGuiRenderer.isLogicalGuiResolutionActive()) {
            int height = com.gtnhvr.gui.VRGuiRenderPipeline.getActiveTargetHeight();
            if (height <= 0) height = com.gtnhvr.gui.VRGuiRenderer.getCaptureHeight();
            if (com.gtnhvr.gui.VRHudRenderer.isCapturing()) height = com.gtnhvr.gui.VRHudRenderer.getHeight();
            if (height > 0) {
                cir.setReturnValue(height);
            }
        }
    }
}
