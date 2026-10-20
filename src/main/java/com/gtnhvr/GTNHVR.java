package com.gtnhvr;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
@Mod(
    modid = GTNHVR.MODID,
    version = Tags.VERSION,
    name = "GTNHVR",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "after:lwjgl3ify",
    acceptableRemoteVersions = "*"
)
public class GTNHVR {

    public static final String MODID = "gtnhvr";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.gtnhvr.ClientProxy", serverSide = "com.gtnhvr.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
