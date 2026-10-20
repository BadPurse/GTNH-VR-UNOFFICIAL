package com.gtnhvr.mixin.early;

import com.gtnhvr.gui.VRGuiRenderer;
import com.gtnhvr.gui.VRHotbarRenderer;
import com.gtnhvr.gui.VRHudRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Ensures org.lwjglx.opengl.GL14.glBlendFuncSeparate preserves destination alpha during VR capture.
 */
@Pseudo
@Mixin(targets = "org.lwjglx.opengl.GL14", remap = false)
public abstract class MixinLWJGLXGL14Alpha {

    @ModifyVariable(method = "glBlendFuncSeparate", at = @At("HEAD"), argsOnly = true, ordinal = 3, remap = false, require = 0)
    private static int gtnhvr$keepCoverageLwjglx(int dstAlpha, int srcRgb, int dstRgb, int srcAlpha) {
        if ((VRGuiRenderer.isCapturing() || VRHudRenderer.isCapturing() || VRHotbarRenderer.isRendering())
                && dstAlpha == GL11.GL_ZERO && srcAlpha == GL11.GL_ONE) {
            return GL11.GL_ONE_MINUS_SRC_ALPHA;
        }
        return dstAlpha;
    }
}
