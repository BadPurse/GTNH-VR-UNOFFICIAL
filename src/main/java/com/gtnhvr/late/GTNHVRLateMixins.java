package com.gtnhvr.late;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@LateMixin
public class GTNHVRLateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.gtnhvr.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (!cpw.mods.fml.relauncher.FMLLaunchHandler.side().isClient()) {
            return java.util.Collections.emptyList();
        }
        List<String> mixins = new ArrayList<>();
        if (loadedMods.contains("angelica")) {
            mixins.add("MixinBatchingFontRendererAlpha");
            mixins.add("MixinReeseSodiumVideoOptionsScreen");
            mixins.add("MixinIrisHandRenderer");
            mixins.add("MixinGLStateManagerAlpha");
            mixins.add("MixinIrisGuiSlot");
        }
        return mixins;
    }
}
