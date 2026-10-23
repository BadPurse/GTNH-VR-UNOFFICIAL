package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes only Minecraft's full-screen world-darkening backdrop for in-world VR screens.
 *
 * <p>Do not globally alter color masks or cancel ordinary drawRect/gradient calls. Those calls
 * are also used by modded widgets, GTNH machine screens and tooltips; they must write proper
 * RGBA into the virtual target just as they do in Visor's per-overlay render targets.</p>
 */
@Mixin(Gui.class)
public abstract class MixinGui {
    @Inject(method = {"drawGradientRect", "func_73733_a"},
            at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancelWorldDarkeningBackdrop(int left, int top, int right, int bottom,
                                                     int startColor, int endColor, CallbackInfo ci) {
        if (!OpenXRRuntime.isSessionRunning()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.currentScreen == null) return;

        // Keep this narrow: gradients used inside the GUI (progress bars, tabs, tooltips, etc.)
        // are content, not the screen backdrop, and must remain visible in the alpha channel.
        if (left <= 0 && top <= 0
                && right >= mc.currentScreen.width
                && bottom >= mc.currentScreen.height) {
            ci.cancel();
        }
    }
}
