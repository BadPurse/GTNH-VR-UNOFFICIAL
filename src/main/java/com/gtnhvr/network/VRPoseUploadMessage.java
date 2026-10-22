package com.gtnhvr.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Client-to-server pose sample. These fields are for remote rendering, never gameplay authority. */
public final class VRPoseUploadMessage implements IMessage {
    private VRPoseData pose = VRPoseData.empty();

    public VRPoseUploadMessage() {}
    public VRPoseUploadMessage(VRPoseData pose) { this.pose = pose == null ? VRPoseData.empty() : pose.copy(); }

    @Override
    public void fromBytes(ByteBuf buffer) { pose = VRPoseData.read(buffer); }

    @Override
    public void toBytes(ByteBuf buffer) { (pose == null ? VRPoseData.empty() : pose).write(buffer); }

    public static final class Handler implements IMessageHandler<VRPoseUploadMessage, IMessage> {
        @Override
        public IMessage onMessage(VRPoseUploadMessage message, MessageContext context) {
            if (context.side != Side.SERVER || message.pose == null || !message.pose.isWellFormed()) return null;
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (player == null) return null;
            final VRPoseData sample = message.pose.copy();
            // Forge 1.7.10 dispatches SimpleImpl handlers on the game thread. The task scheduler
            // used by Minecraft 1.8+ does not exist on this target.
            VRRemotePoseServerState.acceptPose(player, sample);
            return null;
        }
    }
}
