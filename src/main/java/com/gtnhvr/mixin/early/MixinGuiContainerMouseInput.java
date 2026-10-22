package com.gtnhvr.mixin.early;

import com.gtnhvr.gui.VRGuiRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keep raw LWJGL mouse events and Display dimensions in the same physical coordinate
 * space while a container handles input. The GUI itself still uses its logical canvas
 * dimensions, so vanilla's event-to-screen conversion scales exactly once.
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainerMouseInput {
    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("HEAD"))
    private void gtnhvr$beginPhysicalMouseInput(CallbackInfo ci) {
        VRGuiRenderer.beginMouseInputScope();
    }

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("RETURN"))
    private void gtnhvr$endPhysicalMouseInput(CallbackInfo ci) {
        VRGuiRenderer.endMouseInputScope();
    }
}
