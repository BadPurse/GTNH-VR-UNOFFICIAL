package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gui.VRVirtualKeyboard;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField {

    @Inject(method = {"setFocused", "func_146195_b"}, at = @At("TAIL"))
    private void gtnhvr$onSetFocused(boolean isFocused, CallbackInfo ci) {
        if (!OpenXRRuntime.isSessionRunning()) return;
        if (!VRClientSettings.keyboardAutoLayout) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (isFocused) {
            if (mc != null && mc.currentScreen != null) {
                VRVirtualKeyboard.getInstance().showAutoKeyboard(mc.currentScreen);
            }
        } else {
            if (mc == null || (!(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)
                    && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiEditSign)
                    && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiScreenBook)
                    && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiRepair))) {
                if (mc == null || !com.gtnhvr.gui.GuiHelper.hasAnyFocusedTextField(mc.currentScreen)) {
                    VRVirtualKeyboard.getInstance().hideAutoKeyboard();
                }
            }
        }
    }

    @Inject(method = {"mouseClicked", "func_146192_a"}, at = @At("RETURN"))
    private void gtnhvr$onMouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (!OpenXRRuntime.isSessionRunning()) return;
        if (!VRClientSettings.keyboardAutoLayout) return;
        GuiTextField tf = (GuiTextField) (Object) this;
        Minecraft mc = Minecraft.getMinecraft();
        if (tf.isFocused() && mc != null && mc.currentScreen != null) {
            VRVirtualKeyboard.getInstance().showAutoKeyboard(mc.currentScreen);
        }
    }
}
