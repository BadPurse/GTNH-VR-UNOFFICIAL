package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.settings.VRPlayMode;
import me.eigenraven.lwjgl3ify.client.TextFieldHandler;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Early mixin to completely disable lwjgl3ify TextFieldHandler calls to SDL_StartTextInput / SDL_StopTextInput in VR.
 * In OpenXR VR, calling SDL_StartTextInput causes Windows Text Input Framework (CoreUIComponents.dll / IMM32)
 * to spawn an OS IME window on the hijacked OpenGL window handle, which crashes SDL3.dll with an access violation
 * (0xc0000005) and FAST_FAIL_FATAL_APP_EXIT (0xc0000409).
 *
 * Loading this as an EARLY mixin guarantees it is applied before TextFieldHandler can ever be called by GuiChat or any GUI.
 */
@Mixin(value = TextFieldHandler.class, remap = false)
public abstract class MixinTextFieldHandler {

    @Inject(method = "beginTextInput", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtnhvr$cancelBeginTextInput(CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning() || VRClientSettings.vrPlayMode != VRPlayMode.DISABLED) {
            ci.cancel();
            if (VRClientSettings.keyboardAutoLayout) {
                com.gtnhvr.gui.VRVirtualKeyboard.getInstance().showAutoKeyboard();
            }
        }
    }

    @Inject(method = "endTextInput", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtnhvr$cancelEndTextInput(GuiTextField field, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning() || VRClientSettings.vrPlayMode != VRPlayMode.DISABLED) {
            ci.cancel();
            if (VRClientSettings.keyboardAutoLayout) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                if (mc == null || (!(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)
                        && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiEditSign)
                        && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiScreenBook)
                        && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiRepair))) {
                    if (mc == null || !com.gtnhvr.gui.GuiHelper.hasAnyFocusedTextField(mc.currentScreen)) {
                        com.gtnhvr.gui.VRVirtualKeyboard.getInstance().hideAutoKeyboard();
                    }
                }
            }
        }
    }

    @Inject(method = "resetTextInput", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtnhvr$cancelResetTextInput(CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning() || VRClientSettings.vrPlayMode != VRPlayMode.DISABLED) {
            ci.cancel();
        }
    }
}
