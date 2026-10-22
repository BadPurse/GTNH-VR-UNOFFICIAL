package com.gtnhvr.network;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/** Reset the per-connection capability bit when switching between modded and unmodded servers. */
public final class VRNetworkClientEvents {
    public static final VRNetworkClientEvents INSTANCE = new VRNetworkClientEvents();

    private VRNetworkClientEvents() {}

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        VRNetwork.clearServerSupport();
        VRRemotePoseClientState.clear();
        VRPoseSyncClient.reset();
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        VRNetwork.clearServerSupport();
        VRRemotePoseClientState.clear();
        VRPoseSyncClient.reset();
    }
}
