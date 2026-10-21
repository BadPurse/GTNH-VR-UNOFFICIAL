package com.gtnhvr.mixin.early;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures item stack count numbers rendered in inventory, hotbar and NEI
 * always have full alpha (0xFFFFFFFF) and blending enabled, preventing transparent
 * holes in VR GUI capture textures.
 */
@Mixin(RenderItem.class)
public abstract class MixinRenderItem {

    @ModifyArg(
        method = {"renderItemOverlayIntoGUI", "func_94148_a"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I"),
        index = 3,
        require = 0
    )
    private int gtnhvr$opaqueStackCountColor(int color) {
        if ((color & 0xFF000000) == 0) {
            return color | 0xFF000000;
        }
        return color;
    }

    @Inject(
        method = {"renderItemOverlayIntoGUI", "func_94148_a"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I"),
        require = 0
    )
    private void gtnhvr$enableBlendBeforeStackNumber(FontRenderer fr, TextureManager tm, ItemStack is, int x, int y, String custom, CallbackInfo ci) {
        GLStateManager.glEnable(GL11.GL_BLEND);
    }
}
