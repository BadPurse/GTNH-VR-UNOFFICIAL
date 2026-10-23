package com.gtnhvr.mixin.early;

import com.gtnhvr.input.VRMouseBridge;
import com.gtnhvr.gui.VRGuiProfiler;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GuiContainerCreative (scroll bar) polls Mouse.isButtonDown(0) from drawScreen to detect clicks and drags, which never
 * sees the VR pointer. lwjgl3ify may rewrite org.lwjgl.input.Mouse to org.lwjglx.input.Mouse
 * before or after Mixin runs, so both call owners are redirected (require = 0 keeps the one
 * that is absent from failing the config).
 */
@Mixin(GuiContainerCreative.class)
public abstract class MixinGuiContainerCreative {

    @Unique
    private long gtnhvr$creativeBackgroundStartNanos;

    @Inject(method = {"drawGuiContainerBackgroundLayer", "func_146976_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$profileCreativeBackgroundStart(float partialTicks, int mouseX, int mouseY, CallbackInfo ci) {
        gtnhvr$creativeBackgroundStartNanos = VRGuiProfiler.startCpu();
    }

    @Inject(method = {"drawGuiContainerBackgroundLayer", "func_146976_a"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$profileCreativeBackgroundEnd(float partialTicks, int mouseX, int mouseY, CallbackInfo ci) {
        VRGuiProfiler.endCpu("creative-inventory-background", gtnhvr$creativeBackgroundStartNanos);
        gtnhvr$creativeBackgroundStartNanos = 0L;
    }


    @Redirect(method = {"drawScreen", "func_73863_a"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;isButtonDown(I)Z", remap = false),
            require = 0)
    private boolean gtnhvr$vrMouseDownLwjgl(int button) {
        return VRMouseBridge.isButtonDown(button);
    }

    @Redirect(method = {"drawScreen", "func_73863_a"},
            at = @At(value = "INVOKE", target = "Lorg/lwjglx/input/Mouse;isButtonDown(I)Z", remap = false),
            require = 0)
    private boolean gtnhvr$vrMouseDownLwjglx(int button) {
        return VRMouseBridge.isButtonDown(button);
    }
}
