package com.gtnhvr.mixin.early;

import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Marker mixin retained for compatibility. GUI event coordinates are already mapped to the
 * virtual GUI canvas by MixinLWJGLXMouse, so container input must not switch back to physical
 * window dimensions halfway through the same event dispatch.
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainerMouseInput {
}
