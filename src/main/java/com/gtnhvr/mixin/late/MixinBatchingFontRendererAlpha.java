package com.gtnhvr.mixin.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Ensures Angelica's BatchingFontRenderer always treats 24-bit RGB colors (where alpha is 0)
 * as opaque (alpha 0xFF), preventing text and stack quantity numbers from rendering with 0 alpha.
 */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.client.font.BatchingFontRenderer")
public abstract class MixinBatchingFontRendererAlpha {

    @ModifyVariable(
        method = "drawString(FFIZZLjava/lang/CharSequence;II)F",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        remap = false,
        require = 0
    )
    private int gtnhvr$ensureOpaqueFontColor(int color) {
        if ((color & 0xFF000000) == 0) {
            return color | 0xFF000000;
        }
        return color;
    }

    /**
     * Some formatted/shadow glyph paths reach pushTexRect with a 24-bit RGB value
     * after the outer drawString color was already normalized. Normalize at the final
     * per-glyph boundary too, while preserving intentional non-zero alpha values.
     */
    @ModifyVariable(
        method = "pushTexRect(FFFFFIFFFFZ)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        remap = false,
        require = 0
    )
    private int gtnhvr$ensureOpaqueGlyphColor(int rgba) {
        return (rgba & 0xFF000000) == 0 ? rgba | 0xFF000000 : rgba;
    }
}
