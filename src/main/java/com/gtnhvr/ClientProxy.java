package com.gtnhvr;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.lwjgl.Version;
import org.lwjgl.openxr.XR10;

@Lwjgl3Aware
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        VRConfig.init(new java.io.File(event.getModConfigurationDirectory(), "gtnhvr.cfg"));
        com.gtnhvr.settings.VRClientSettings.init(new java.io.File(event.getModConfigurationDirectory(), "visor.cfg"));
        com.gtnhvr.settings.VROverlayConfig.init(new java.io.File(event.getModConfigurationDirectory(), "gtnhvr_overlays.cfg"));
        GTNHVRLog.info("ClientProxy preInit: Java %s, LWJGL %s",
                System.getProperty("java.version"), Version.getVersion());
        try {
            String xrVersion = XR10.class.getName();
            GTNHVRLog.info("OpenXR API loaded: %s", xrVersion);
        } catch (Throwable t) {
            GTNHVRLog.warn("Failed to load OpenXR classes: %s", t);
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // The "visor" resource domain ships inside this jar (assets/visor), so FML already
        // exposes it. A second resource pack plus Minecraft.refreshResources() here only
        // forced a redundant full resource reload (~18 s in latest.log).
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(com.gtnhvr.network.VRNetworkClientEvents.INSTANCE);
        ClientLifecycle.register();
        com.gtnhvr.gui.GuiEventHandler.register();
        KeyBindings.register();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.gtnhvr.gameplay.tasks.VRTaskBow.getInstance());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
