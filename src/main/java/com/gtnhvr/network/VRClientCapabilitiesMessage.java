package com.gtnhvr.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Opt-in acknowledgement that this client understands GTNHVR's versioned pose relay. */
public final class VRClientCapabilitiesMessage implements IMessage {
    public static final int POSE_PROTOCOL_VERSION = 1;
    private int poseProtocolVersion = POSE_PROTOCOL_VERSION;

    public VRClientCapabilitiesMessage() {}
    public VRClientCapabilitiesMessage(int version) { poseProtocolVersion = version; }

    @Override
    public void fromBytes(ByteBuf buffer) { poseProtocolVersion = buffer.readInt(); }

    @Override
    public void toBytes(ByteBuf buffer) { buffer.writeInt(poseProtocolVersion); }

    public static final class Handler implements IMessageHandler<VRClientCapabilitiesMessage, IMessage> {
        @Override
        public IMessage onMessage(VRClientCapabilitiesMessage message, MessageContext context) {
            if (context.side != Side.SERVER || message.poseProtocolVersion != POSE_PROTOCOL_VERSION) return null;
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (player == null) return null;
            // Forge 1.7.10 dispatches SimpleImpl handlers on the game thread. The task scheduler
            // used by Minecraft 1.8+ does not exist on this target.
            VRRemotePoseServerState.clientJoinedVR(player);
            return null;
        }
    }
}
