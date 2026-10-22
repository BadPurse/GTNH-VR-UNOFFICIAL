package com.gtnhvr;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;
import com.gtnhvr.network.VRNetwork;
import com.gtnhvr.network.VRBowServerHandler;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        VRNetwork.init();
    }

    public void init(FMLInitializationEvent event) {
        // Bow tension/aim affects server-authoritative projectile creation.
        MinecraftForge.EVENT_BUS.register(VRBowServerHandler.INSTANCE);
        FMLCommonHandler.instance().bus().register(VRBowServerHandler.INSTANCE);
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
