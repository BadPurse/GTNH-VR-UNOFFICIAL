package com.gtnhvr.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;

/** Client-to-server physical bow draw fraction and launch angles; values are validated server-side. */
public final class VRBowAimMessage implements IMessage {
    private float tension;
    private float yaw;
    private float pitch;

    public VRBowAimMessage() {}

    public VRBowAimMessage(float tension, float yaw, float pitch) {
        this.tension = tension;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        tension = buffer.readFloat();
        yaw = buffer.readFloat();
        pitch = buffer.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeFloat(tension);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
    }

    public static final class Handler implements IMessageHandler<VRBowAimMessage, IMessage> {
        @Override
        public IMessage onMessage(VRBowAimMessage message, MessageContext context) {
            if (context.side != Side.SERVER) return null;
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (player == null || player.worldObj == null || player.worldObj.isRemote) return null;
            if (!finite(message.tension) || !finite(message.yaw) || !finite(message.pitch)) return null;
            if (message.tension < 0.0F || message.tension > 1.0F
                    || message.yaw < -180.0F || message.yaw > 180.0F
                    || message.pitch < -90.0F || message.pitch > 90.0F) return null;
            // Require the server's current use-item stack to actually be a bow. Offhand VR bows
            // temporarily swap their selected slot before beginning the normal use-item action.
            ItemStack using = player.getItemInUse();
            if (!player.isUsingItem() || using == null || !(using.getItem() instanceof ItemBow)
                    || !hasBowInHotbar(player)) return null;
            VRBowServerState.set(player, message.tension, message.yaw, message.pitch);
            return null;
        }

        private static boolean hasBowInHotbar(EntityPlayerMP player) {
            ItemStack held = player.getHeldItem();
            if (held != null && held.getItem() instanceof ItemBow) return true;
            if (player.inventory == null || player.inventory.mainInventory == null) return false;
            int limit = Math.min(9, player.inventory.mainInventory.length);
            for (int i = 0; i < limit; i++) {
                ItemStack stack = player.inventory.mainInventory[i];
                if (stack != null && stack.getItem() instanceof ItemBow) return true;
            }
            return false;
        }

        private static boolean finite(float value) {
            return !Float.isNaN(value) && !Float.isInfinite(value);
        }
    }
}
