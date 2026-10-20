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
}
