package com.gtnhvr.core;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({"com.gtnhvr.core."})
@IFMLLoadingPlugin.Name("GTNHVR-Coremod")
public class GTNHVRCoremod implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.gtnhvr.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return java.util.Arrays.asList(
                "MixinEntity",
                "MixinEntityRenderer",
                "MixinGui",
                "MixinGuiIngame",
                "MixinGuiContainerCreative",
                "MixinGuiMainMenu",
                "MixinGuiScreen",
                "MixinGuiScrollingList",
                "MixinGuiSlot",
                "MixinGuiTextField",
                "MixinLWJGLXGL11Alpha",
                "MixinLWJGLXGL14Alpha",
                "MixinLWJGLXDisplay",
                "MixinMinecraft",
                "MixinMovementInputFromOptions",
                "MixinTextFieldHandler"
        );
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
