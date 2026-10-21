package com.gtnhvr.mixin.early;

import com.gtnhvr.gui.VRGuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Match queued LWJGL mouse events to the virtual pixel resolution used by VR GUI capture.
 * This covers machine/mod GUIs that drain Mouse.next() directly without calling
 * GuiScreen.handleMouseInput(), where the standard input-dimension scope cannot run.
 */
@Pseudo
@Mixin(targets = {"org.lwjglx.input.Mouse", "org.lwjgl.input.Mouse"}, remap = false)
public abstract class MixinLWJGLXMouse {

    @Inject(method = "getEventX", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$mapEventXToVirtualCanvas(CallbackInfoReturnable<Integer> cir) {
        int x = cir.getReturnValue();
        int mapped = VRGuiRenderer.mapPhysicalMouseEventXToLogicalCanvas(x);
        if (mapped != x) {
            cir.setReturnValue(mapped);
        }
    }

    @Inject(method = "getEventY", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void gtnhvr$mapEventYToVirtualCanvas(CallbackInfoReturnable<Integer> cir) {
        int y = cir.getReturnValue();
        int mapped = VRGuiRenderer.mapPhysicalMouseEventYToLogicalCanvas(y);
        if (mapped != y) {
            cir.setReturnValue(mapped);
        }
    }
}
