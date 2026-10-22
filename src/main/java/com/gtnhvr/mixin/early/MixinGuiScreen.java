package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    private long gtnhvr$baseScreenDrawStartNanos;

    @Inject(method = {"drawScreen", "func_73863_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$profileBaseScreenDrawStart(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        gtnhvr$baseScreenDrawStartNanos = com.gtnhvr.gui.VRGuiProfiler.startCpu();
    }

    @Inject(method = {"drawScreen", "func_73863_a"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$profileBaseScreenDrawEnd(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        com.gtnhvr.gui.VRGuiProfiler.endCpu(
                "gui-screen-base-draw/" + ((GuiScreen) (Object) this).getClass().getName(),
                gtnhvr$baseScreenDrawStartNanos);
        gtnhvr$baseScreenDrawStartNanos = 0L;
    }

    @Inject(method = {"setWorldAndResolution", "func_146280_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$profileResolutionInit(Minecraft mc, int width, int height, CallbackInfo ci) {
        com.gtnhvr.gui.VRGuiProfiler.beginScreenResolution((GuiScreen) (Object) this);
    }

    @Inject(method = {"setWorldAndResolution", "func_146280_a"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$finishResolutionInit(Minecraft mc, int width, int height, CallbackInfo ci) {
        com.gtnhvr.gui.VRGuiProfiler.endScreenResolution((GuiScreen) (Object) this);
    }

    @Inject(method = {"initGui", "func_73866_w_"}, at = @At("HEAD"))
    private void gtnhvr$closeKeyboardForUnrelatedScreen(CallbackInfo ci) {
        com.gtnhvr.gui.VRGuiProfiler.beginInitGui((GuiScreen) (Object) this);
        com.gtnhvr.gui.VRVirtualKeyboard.getInstance().onGuiOpen((GuiScreen) (Object) this);
    }

    @Inject(method = {"initGui", "func_73866_w_"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$profileInitGuiEnd(CallbackInfo ci) {
        com.gtnhvr.gui.VRGuiProfiler.endInitGui((GuiScreen) (Object) this);
    }

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("HEAD"))
    private void gtnhvr$beginPhysicalMouseInput(CallbackInfo ci) {
        com.gtnhvr.gui.VRGuiRenderer.beginMouseInputScope();
    }

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("RETURN"))
    private void gtnhvr$endPhysicalMouseInput(CallbackInfo ci) {
        com.gtnhvr.gui.VRGuiRenderer.endMouseInputScope();
    }

    @Inject(method = {"drawWorldBackground", "func_146270_b"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancelWorldBackground(int tint, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning() && mc != null && mc.theWorld != null) {
            ci.cancel();
        }
    }
}
