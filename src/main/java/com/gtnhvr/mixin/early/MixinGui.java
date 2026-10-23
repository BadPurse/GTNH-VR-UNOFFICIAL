package com.gtnhvr.mixin.early;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Intentionally has no global drawRect/drawGradientRect state overrides.
 *
 * The capture target stores premultiplied alpha. Global color-mask changes here corrupted
 * alpha for semi-transparent widgets from NEI, GregTech, and other mods. Vanilla world
 * background suppression is scoped to GuiScreen.drawWorldBackground in MixinGuiScreen,
 * rather than cancelling arbitrary full-screen gradients.
 */
@Mixin(Gui.class)
public abstract class MixinGui {
}
