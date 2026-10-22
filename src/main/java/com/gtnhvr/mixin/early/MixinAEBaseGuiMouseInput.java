package com.gtnhvr.mixin.early;

import com.gtnhvr.gui.VRGuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2's AEBaseGui overrides handleMouseInput(): it calls GuiContainer.super first,
 * then calculates its own scroll coordinates from Mouse.getEventX/Y and mc.displayWidth/Height.
 * The GuiContainer scope alone ends before this AE2-specific part runs. Keep the physical
 * display dimensions active for the whole override so the custom scrollbar uses the same
 * coordinate space as the queued mouse event.
 */
@Pseudo
@Mixin(targets = "appeng.client.gui.AEBaseGui")
public abstract class MixinAEBaseGuiMouseInput {

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$beginAe2MouseInput(CallbackInfo ci) {
        VRGuiRenderer.beginMouseInputScope();
    }

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$endAe2MouseInput(CallbackInfo ci) {
        VRGuiRenderer.endMouseInputScope();
    }
}
