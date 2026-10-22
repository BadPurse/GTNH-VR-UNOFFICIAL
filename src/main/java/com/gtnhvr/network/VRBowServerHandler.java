package com.gtnhvr.network;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;

/** Server-side listeners for physical VR bow draw and launch data. */
public final class VRBowServerHandler {
    public static final VRBowServerHandler INSTANCE = new VRBowServerHandler();

    private VRBowServerHandler() {}

    @SubscribeEvent
    public void onArrowLoose(ArrowLooseEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        VRBowServerState.LaunchState state = VRBowServerState.getRecent(player);
        if (state == null) return;

        // Vanilla computes arrow velocity from this charge. Match the client's physical pull
        // fraction rather than elapsed server ticks.
        event.charge = Math.max(0, Math.min(20, Math.round(state.tension * 20.0F)));
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.worldObj == null || player.worldObj.isRemote) return;
        VRNetwork.sendBowHandshakeTo(player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        VRBowServerState.clear(event.player);
    }
}
