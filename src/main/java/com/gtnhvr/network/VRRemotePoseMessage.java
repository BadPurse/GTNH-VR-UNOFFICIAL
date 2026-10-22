package com.gtnhvr.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import java.util.UUID;

/** Server-to-client remote pose sample for a GTNHVR peer. */
public final class VRRemotePoseMessage implements IMessage {
    private UUID playerId;
    private VRPoseData pose = VRPoseData.empty();

    public VRRemotePoseMessage() {}
    public VRRemotePoseMessage(UUID playerId, VRPoseData pose) {
        this.playerId = playerId;
        this.pose = pose == null ? VRPoseData.empty() : pose.copy();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        playerId = new UUID(buffer.readLong(), buffer.readLong());
        pose = VRPoseData.read(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        UUID id = playerId == null ? new UUID(0L, 0L) : playerId;
        buffer.writeLong(id.getMostSignificantBits());
        buffer.writeLong(id.getLeastSignificantBits());
        (pose == null ? VRPoseData.empty() : pose).write(buffer);
    }

    public static final class Handler implements IMessageHandler<VRRemotePoseMessage, IMessage> {
        @Override
        public IMessage onMessage(VRRemotePoseMessage message, MessageContext context) {
            if (context.side != Side.CLIENT || message.playerId == null
                    || message.pose == null || !message.pose.isWellFormed()) return null;
            VRRemotePoseClientState.accept(message.playerId, message.pose);
            return null;
        }
    }
}
