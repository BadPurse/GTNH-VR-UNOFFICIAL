package com.gtnhvr.mixin.early;

import com.gtnhvr.gui.VRGuiRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GuiContainer implementations (including GTNH machine GUIs) can poll the LWJGL mouse queue
 * while another mod is rendering. Keep their input pass in the physical window coordinate
 * space even though the displayed GUI canvas is rendered at the fixed VR resolution.
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainerMouseInput {

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$beginPhysicalMouseInputScope(CallbackInfo ci) {
        VRGuiRenderer.beginMouseInputScope();
    }

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$endPhysicalMouseInputScope(CallbackInfo ci) {
        VRGuiRenderer.endMouseInputScope();
    }
}
