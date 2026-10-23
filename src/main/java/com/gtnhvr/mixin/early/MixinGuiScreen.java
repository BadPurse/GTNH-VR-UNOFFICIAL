package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import com.gtnhvr.gui.VRGuiRenderer;
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

    @Inject(method = {"initGui", "func_73866_w_"}, at = @At("HEAD"))
    private void gtnhvr$closeKeyboardForUnrelatedScreen(CallbackInfo ci) {
        com.gtnhvr.gui.VRVirtualKeyboard.getInstance().onGuiOpen((GuiScreen) (Object) this);
    }


    @Inject(method = {"setWorldAndResolution", "func_146280_a"}, at = @At("TAIL"), require = 0)
    private void gtnhvr$rememberLogicalGuiScreen(net.minecraft.client.Minecraft mc,
                                                  int width, int height, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            VRGuiRenderer.noteLogicalGuiScreen((GuiScreen) (Object) this);
        }
    }

    @Inject(method = {"drawWorldBackground", "func_146270_b"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancelWorldBackground(int tint, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning() && mc != null && mc.theWorld != null) {
            ci.cancel();
        }
    }
}
