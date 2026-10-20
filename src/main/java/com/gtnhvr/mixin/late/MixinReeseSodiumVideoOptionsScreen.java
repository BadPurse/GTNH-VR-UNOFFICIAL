package com.gtnhvr.mixin.late;

import com.gtnhvr.gui.angelica.VROptionPages;
import me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ReeseSodiumVideoOptionsScreen.class, remap = false)
public abstract class MixinReeseSodiumVideoOptionsScreen {

    @Shadow @Final
    private List<OptionPage> pages;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtnhvr$addVRPage(GuiScreen prevScreen, CallbackInfo ci) {
        this.pages.add(VROptionPages.vr((GuiScreen) (Object) this));
    }
}
