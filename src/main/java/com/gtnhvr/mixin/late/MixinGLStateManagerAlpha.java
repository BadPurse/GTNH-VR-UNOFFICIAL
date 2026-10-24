package com.gtnhvr.mixin.late;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.gui.VRGuiRenderer;
import com.gtnhvr.gui.VRHotbarRenderer;
import com.gtnhvr.gui.VRHudRenderer;
import com.gtnhvr.gui.VRGuiRenderPipeline;
import com.gtnhvr.gui.VRGuiProfiler;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla and NEI draw with glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA), which also blends the
 * destination alpha and punches holes into the offscreen VR GUI texture. While a VR capture
 * is active every such call is turned into a separate-alpha blend that keeps accumulating alpha.
 */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.glsm.GLStateManager")
public abstract class MixinGLStateManagerAlpha {

    @Inject(method = "glPushAttrib", at = @At("HEAD"), remap = false, require = 0)
    private static void gtnhvr$countAttribPush(int mask, CallbackInfo ci) {
        VRGuiProfiler.attribPush();
    }

    @Inject(method = "glEnable", at = @At("HEAD"), remap = false, require = 0)
    private static void gtnhvr$lazyStencilClear(int capability, CallbackInfo ci) {
        if (capability == GL11.GL_STENCIL_TEST) {
            VRGuiRenderPipeline.prepareStencilForGuiUse();
        }
    }

    @Inject(method = "glBlendFunc", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$alphaSafeBlend(int srcFactor, int dstFactor, CallbackInfo ci) {
        if (VRGuiRenderer.isCapturing() || VRHudRenderer.isCapturing() || VRHotbarRenderer.isRendering()) {
            GLStateManager.glBlendFuncSeparate(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            ci.cancel();
        }
    }

    @ModifyVariable(method = "tryBlendFuncSeparate", at = @At("HEAD"), argsOnly = true, ordinal = 3, remap = false, require = 0)
    private static int gtnhvr$keepCoverageTry(int dstAlpha, int srcRgb, int dstRgb, int srcAlpha) {
        if ((VRGuiRenderer.isCapturing() || VRHudRenderer.isCapturing() || VRHotbarRenderer.isRendering())
                && dstAlpha == GL11.GL_ZERO && srcAlpha == GL11.GL_ONE) {
            return GL11.GL_ONE_MINUS_SRC_ALPHA;
        }
        return dstAlpha;
    }

    @ModifyVariable(method = "glBlendFuncSeparate", at = @At("HEAD"), argsOnly = true, ordinal = 3, remap = false, require = 0)
    private static int gtnhvr$keepCoverageSeparate(int dstAlpha, int srcRgb, int dstRgb, int srcAlpha) {
        if ((VRGuiRenderer.isCapturing() || VRHudRenderer.isCapturing() || VRHotbarRenderer.isRendering())
                && dstAlpha == GL11.GL_ZERO && srcAlpha == GL11.GL_ONE) {
            return GL11.GL_ONE_MINUS_SRC_ALPHA;
        }
        return dstAlpha;
    }
}
