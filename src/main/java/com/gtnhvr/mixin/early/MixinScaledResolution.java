package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gui.VRGuiRenderer;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps Minecraft's logical GUI coordinate space independent of the physical lwjgl3ify window.
 *
 * <p>Vanilla and Forge create ScaledResolution objects in dozens of GUI, HUD, inventory and mod
 * paths. Overriding the explicit dimensions here gives those callers the same virtual canvas,
 * without lying to Angelica or other code that reads Minecraft.displayWidth/displayHeight.</p>
 */
@Lwjgl3Aware
@Mixin(ScaledResolution.class)
public abstract class MixinScaledResolution {
    @ModifyVariable(
            method = "<init>(Lnet/minecraft/client/Minecraft;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0)
    private int gtnhvr$useVirtualGuiWidth(int originalWidth) {
        if (!OpenXRRuntime.isSessionRunning()) return originalWidth;
        return VRGuiRenderer.getCaptureWidth();
    }

    @ModifyVariable(
            method = "<init>(Lnet/minecraft/client/Minecraft;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            require = 0)
    private int gtnhvr$useVirtualGuiHeight(int originalHeight) {
        if (!OpenXRRuntime.isSessionRunning()) return originalHeight;
        return VRGuiRenderer.getCaptureHeight();
    }
}
