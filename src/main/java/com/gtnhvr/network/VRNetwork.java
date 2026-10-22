package com.gtnhvr.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Common Forge 1.7.10 network channel. Bow payloads are sent only after the remote server
 * explicitly acknowledges this channel, so GTNHVR remains safe to use on unmodded servers.
 */
public final class VRNetwork {
    private static volatile SimpleNetworkWrapper channel;
    private static volatile boolean serverSupportsBowAim;

    private VRNetwork() {}

    public static synchronized void init() {
        if (channel != null) return;
        SimpleNetworkWrapper created = NetworkRegistry.INSTANCE.newSimpleChannel("gtnhvr");
        created.registerMessage(VRBowAimMessage.Handler.class, VRBowAimMessage.class, 0, Side.SERVER);
        created.registerMessage(VRBowNetworkHandshake.Handler.class, VRBowNetworkHandshake.class, 1, Side.CLIENT);
        channel = created;
    }

    public static void sendBowAim(float tension, float yaw, float pitch) {
        SimpleNetworkWrapper current = channel;
        if (current == null || !serverSupportsBowAim) return;
        if (!finite(tension) || !finite(yaw) || !finite(pitch)) return;
        current.sendToServer(new VRBowAimMessage(tension, yaw, pitch));
    }

    public static void sendBowHandshakeTo(EntityPlayerMP player) {
        SimpleNetworkWrapper current = channel;
        if (current != null && player != null) current.sendTo(new VRBowNetworkHandshake(), player);
    }

    public static void markServerSupportsBowAim() {
        serverSupportsBowAim = true;
    }

    public static void clearServerSupport() {
        serverSupportsBowAim = false;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
