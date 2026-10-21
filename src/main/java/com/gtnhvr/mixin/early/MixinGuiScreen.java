package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures 100% transparent background for in-game GUIs in VR (inventories, chests, pause menu, machines),
 * eliminating vanilla Minecraft's 75% dark tint overlay (0xC0101010).
 * Matches Visor's ScreenMixin behavior.
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {

    @Inject(method = {"drawWorldBackground", "func_146270_b"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancelWorldBackground(int tint, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning() && mc != null && mc.theWorld != null) {
            ci.cancel();
        }
    }
    /**
     * Modded GUI frameworks may drain Mouse.next() while rendering (rather than during
     * Minecraft's normal input tick). Keep those queued window-pixel events paired with
     * the physical window dimensions; the surrounding GUI canvas remains virtual 1920x1080.
     */
    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$beginPhysicalMouseInputScope(CallbackInfo ci) {
        com.gtnhvr.gui.VRGuiRenderer.beginMouseInputScope();
    }

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$endPhysicalMouseInputScope(CallbackInfo ci) {
        com.gtnhvr.gui.VRGuiRenderer.endMouseInputScope();
    }

}
