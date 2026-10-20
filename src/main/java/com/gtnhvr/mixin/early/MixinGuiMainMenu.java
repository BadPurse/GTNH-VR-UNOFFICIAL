package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    /**
     * Disables the flat 2D rotating panorama skybox in the main menu when VR is active,
     * so the authentic 3D VRMenuEnvironment (rotating panorama & platform) is visible
     * behind the floating VR title screen. Matches Visor's TitleScreenMixin 1:1.
     */
    @Inject(method = {"renderSkybox", "func_73971_c"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancel2dSkybox(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            ci.cancel();
        }
    }
}
