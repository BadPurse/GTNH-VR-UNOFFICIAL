package com.gtnhvr.mixin.early;

import com.gtnhvr.gui.VRGuiRenderer;
import com.gtnhvr.gui.VRGuiProfiler;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    private long gtnhvr$slotDrawStartNanos;

    @Inject(method = {"drawSlot", "func_146977_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$profileSlotDrawStart(Slot slot, CallbackInfo ci) {
        gtnhvr$slotDrawStartNanos = VRGuiProfiler.startCpu();
    }

    @Inject(method = {"drawSlot", "func_146977_a"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$profileSlotDrawEnd(Slot slot, CallbackInfo ci) {
        VRGuiProfiler.endCpu("container-slot-draw", gtnhvr$slotDrawStartNanos);
        gtnhvr$slotDrawStartNanos = 0L;
    }


    @Unique
    private long gtnhvr$containerDrawStartNanos;

    @Inject(method = {"drawScreen", "func_73863_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$profileContainerDrawStart(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        gtnhvr$containerDrawStartNanos = VRGuiProfiler.startCpu();
    }

    @Inject(method = {"drawScreen", "func_73863_a"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$profileContainerDrawEnd(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        VRGuiProfiler.endCpu(
                "container-base-draw/" + ((GuiContainer) (Object) this).getClass().getName(),
                gtnhvr$containerDrawStartNanos);
        gtnhvr$containerDrawStartNanos = 0L;
    }

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("HEAD"))
    private void gtnhvr$beginPhysicalMouseInput(CallbackInfo ci) {
        VRGuiRenderer.beginMouseInputScope();
    }

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("RETURN"))
    private void gtnhvr$endPhysicalMouseInput(CallbackInfo ci) {
        VRGuiRenderer.endMouseInputScope();
    }
}
