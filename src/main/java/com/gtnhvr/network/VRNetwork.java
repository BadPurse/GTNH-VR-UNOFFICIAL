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
    private static volatile boolean serverSupportsPoseSync;
    private static volatile boolean clientCapabilitiesSent;

    private VRNetwork() {}

    public static synchronized void init() {
        if (channel != null) return;
        SimpleNetworkWrapper created = NetworkRegistry.INSTANCE.newSimpleChannel("gtnhvr");
        created.registerMessage(VRBowAimMessage.Handler.class, VRBowAimMessage.class, 0, Side.SERVER);
        created.registerMessage(VRBowNetworkHandshake.Handler.class, VRBowNetworkHandshake.class, 1, Side.CLIENT);
        created.registerMessage(VRClientCapabilitiesMessage.Handler.class, VRClientCapabilitiesMessage.class, 2, Side.SERVER);
        created.registerMessage(VRPoseUploadMessage.Handler.class, VRPoseUploadMessage.class, 3, Side.SERVER);
        created.registerMessage(VRRemotePoseMessage.Handler.class, VRRemotePoseMessage.class, 4, Side.CLIENT);
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

    /** Protocol v1 supports legacy bow packets; v2 adds opt-in remote pose relay. */
    public static void markServerSupportsBowAim(int handshakeVersion) {
        serverSupportsBowAim = true;
        serverSupportsPoseSync = handshakeVersion >= 2;
        if (serverSupportsPoseSync && !clientCapabilitiesSent) {
            SimpleNetworkWrapper current = channel;
            if (current != null) {
                clientCapabilitiesSent = true;
                current.sendToServer(new VRClientCapabilitiesMessage(VRClientCapabilitiesMessage.POSE_PROTOCOL_VERSION));
            }
        }
    }

    public static boolean serverSupportsPoseSync() {
        return serverSupportsPoseSync;
    }

    public static void sendPose(VRPoseData pose) {
        SimpleNetworkWrapper current = channel;
        if (current == null || !serverSupportsPoseSync || pose == null || !pose.isWellFormed()) return;
        current.sendToServer(new VRPoseUploadMessage(pose));
    }

    public static void sendRemotePoseToPlayer(EntityPlayerMP subject, VRPoseData pose, EntityPlayerMP recipient) {
        SimpleNetworkWrapper current = channel;
        if (current == null || subject == null || recipient == null || pose == null
                || !VRRemotePoseServerState.hasClient(recipient)) return;
        current.sendTo(new VRRemotePoseMessage(subject.getUniqueID(), pose), recipient);
    }

    public static void broadcastRemotePose(EntityPlayerMP subject, VRPoseData pose) {
        if (subject == null || subject.worldObj == null || subject.worldObj.isRemote || pose == null) return;
        for (Object value : subject.worldObj.playerEntities) {
            if (!(value instanceof EntityPlayerMP)) continue;
            EntityPlayerMP recipient = (EntityPlayerMP) value;
            if (recipient != subject && VRRemotePoseServerState.hasClient(recipient)) {
                sendRemotePoseToPlayer(subject, pose, recipient);
            }
        }
    }

    public static void clearServerSupport() {
        serverSupportsBowAim = false;
        serverSupportsPoseSync = false;
        clientCapabilitiesSent = false;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
