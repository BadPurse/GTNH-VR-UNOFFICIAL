package com.gtnhvr.mixin.early;

import com.gtnhvr.gui.VRGuiRenderer;
import com.gtnhvr.gui.VRHotbarRenderer;
import com.gtnhvr.gui.VRHudRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures direct LWJGL calls to GL11.glBlendFunc(src, dst) (e.g. from NEI or mods)
 * preserve destination alpha during VR GUI capture, preventing semi-transparent
 * holes, z-fighting and flickering between stereo eyes.
 */
@Pseudo
@Mixin(targets = "org.lwjglx.opengl.GL11", remap = false)
public abstract class MixinLWJGLXGL11Alpha {

    @Inject(method = "glBlendFunc", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$alphaSafeBlend(int srcFactor, int dstFactor, CallbackInfo ci) {
        if (VRGuiRenderer.isCapturing() || VRHudRenderer.isCapturing() || VRHotbarRenderer.isRendering()) {
            GL14.glBlendFuncSeparate(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            ci.cancel();
        }
    }
}
