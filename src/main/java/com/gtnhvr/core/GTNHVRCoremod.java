package com.gtnhvr.core;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
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
        if (!FMLLaunchHandler.side().isClient()) {
            // Server-side counterpart to the client-only bow aiming mixin.
            return Collections.singletonList("MixinItemBowAimServer");
        }
        return java.util.Arrays.asList(
                "MixinItemBowAimServer",
                "MixinEntity",
                "MixinEntityRenderer",
                "MixinItemBowAim",
                "MixinGui",
                "MixinGuiContainerCreative",
                "MixinGuiIngame",
                "MixinGuiIngameForge",
                "MixinGuiMainMenu",
                "MixinGuiScreen",
                "MixinGuiContainerMouseInput",
                "MixinAEBaseGuiMouseInput",
                "MixinGuiScrollingList",
                "MixinGuiSlot",
                "MixinGuiTextField",
                "MixinItemRenderer",
                "MixinLWJGLXDisplay",
                "MixinLWJGLXMouse",
                "MixinLWJGLXGL11Alpha",
                "MixinLWJGLXGL14Alpha",
                "MixinMinecraft",
                "MixinMovementInputFromOptions",
                "MixinPlayerControllerMP",
                "MixinRenderItem",
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
