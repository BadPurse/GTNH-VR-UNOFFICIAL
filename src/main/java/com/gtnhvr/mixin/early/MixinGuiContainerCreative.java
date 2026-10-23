package com.gtnhvr.mixin.early;

import com.gtnhvr.gui.VRGuiTooltipCache;
import com.gtnhvr.input.VRMouseBridge;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Keeps the creative screen's VR pointer working and avoids regenerating complex modded item
 * tooltips on every frame while the pointer remains over the same stack.
 */
@Mixin(GuiContainerCreative.class)
public abstract class MixinGuiContainerCreative {

    @Redirect(method = {"drawScreen", "func_73863_a"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;isButtonDown(I)Z", remap = false),
            require = 0)
    private boolean gtnhvr$vrMouseDownLwjgl(int button) {
        return VRMouseBridge.isButtonDown(button);
    }

    @Redirect(method = {"drawScreen", "func_73863_a"},
            at = @At(value = "INVOKE", target = "Lorg/lwjglx/input/Mouse;isButtonDown(I)Z", remap = false),
            require = 0)
    private boolean gtnhvr$vrMouseDownLwjglx(int button) {
        return VRMouseBridge.isButtonDown(button);
    }

    @Redirect(method = {"renderToolTip", "func_146285_a"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;getTooltip(Lnet/minecraft/entity/player/EntityPlayer;Z)Ljava/util/List;"),
            require = 0)
    private List gtnhvr$cachedCreativeItemTooltip(ItemStack stack, EntityPlayer player, boolean advanced) {
        return VRGuiTooltipCache.getTooltip(stack, player, advanced);
    }
}
