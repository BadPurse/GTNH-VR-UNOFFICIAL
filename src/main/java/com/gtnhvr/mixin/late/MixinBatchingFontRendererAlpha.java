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
}
