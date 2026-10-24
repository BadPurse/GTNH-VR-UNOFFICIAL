package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gui.VRGuiRenderer;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Maps raw LWJGL cursor/event pixels into the active VR GUI target only while a GUI resolution
 * scope is active. World rendering and ordinary desktop input always retain physical coordinates.
 */
@Pseudo
@Lwjgl3Aware
@Mixin(targets = {"org.lwjglx.input.Mouse", "org.lwjgl.input.Mouse"}, remap = false)
public abstract class MixinLWJGLXMouse {

    @Inject(method = "getX", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$mapCursorX(CallbackInfoReturnable<Integer> cir) {
        if (OpenXRRuntime.isSessionRunning() && VRGuiRenderer.isLogicalGuiResolutionActive()) {
            cir.setReturnValue(VRGuiRenderer.mapPhysicalMouseEventXToLogicalCanvas(cir.getReturnValueI()));
        }
    }

    @Inject(method = "getY", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$mapCursorY(CallbackInfoReturnable<Integer> cir) {
        if (OpenXRRuntime.isSessionRunning() && VRGuiRenderer.isLogicalGuiResolutionActive()) {
            cir.setReturnValue(VRGuiRenderer.mapPhysicalMouseEventYToLogicalCanvas(cir.getReturnValueI()));
        }
    }

    @Inject(method = "getEventX", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$mapEventX(CallbackInfoReturnable<Integer> cir) {
        if (OpenXRRuntime.isSessionRunning() && VRGuiRenderer.isLogicalGuiResolutionActive()) {
            cir.setReturnValue(VRGuiRenderer.mapPhysicalMouseEventXToLogicalCanvas(cir.getReturnValueI()));
        }
    }

    @Inject(method = "getEventY", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$mapEventY(CallbackInfoReturnable<Integer> cir) {
        if (OpenXRRuntime.isSessionRunning() && VRGuiRenderer.isLogicalGuiResolutionActive()) {
            cir.setReturnValue(VRGuiRenderer.mapPhysicalMouseEventYToLogicalCanvas(cir.getReturnValueI()));
        }
    }
}
