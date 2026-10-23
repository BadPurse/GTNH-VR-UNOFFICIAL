package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gui.VRGuiRenderer;
import com.gtnhvr.gui.VRGuiTooltipCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Keeps VR screen lifecycle behavior and avoids recomputing expensive modded item tooltips on
 * each draw frame while the same stack remains under the cursor.
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

    @Redirect(method = {"renderToolTip", "func_146285_a"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;getTooltip(Lnet/minecraft/entity/player/EntityPlayer;Z)Ljava/util/List;"),
            require = 0)
    private List gtnhvr$cachedItemTooltip(ItemStack stack, EntityPlayer player, boolean advanced) {
        return VRGuiTooltipCache.getTooltip(stack, player, advanced);
    }

    @Inject(method = {"drawWorldBackground", "func_146270_b"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancelWorldBackground(int tint, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning() && mc != null && mc.theWorld != null) {
            ci.cancel();
        }
    }
}
