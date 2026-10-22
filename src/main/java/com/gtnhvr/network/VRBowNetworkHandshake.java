package com.gtnhvr.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Server-to-client acknowledgement that gates custom bow packets on the client. */
public final class VRBowNetworkHandshake implements IMessage {
    private int protocolVersion = 3;

    public VRBowNetworkHandshake() {}

    public int getProtocolVersion() {
        return protocolVersion;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        // Pre-v2 servers send an empty handshake; v2 servers support bow sync but not quaternion pose packets.
        protocolVersion = buffer.readableBytes() >= 4 ? buffer.readInt() : 1;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(protocolVersion);
    }

    public static final class Handler implements IMessageHandler<VRBowNetworkHandshake, IMessage> {
        @Override
        public IMessage onMessage(VRBowNetworkHandshake message, MessageContext context) {
            if (context.side == Side.CLIENT) VRNetwork.markServerSupportsBowAim(message.getProtocolVersion());
            return null;
        }
    }
}
